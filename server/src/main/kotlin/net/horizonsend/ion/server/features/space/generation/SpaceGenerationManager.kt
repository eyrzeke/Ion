package net.horizonsend.ion.server.features.space.generation

import net.horizonsend.ion.server.IonServer.Companion.Ion
import net.horizonsend.ion.server.features.space.generation.generators.GenerateAsteroidTask
import net.horizonsend.ion.server.features.space.generation.generators.GenerateWreckTask
import net.horizonsend.ion.server.features.space.generation.generators.SpaceGenerator
import net.horizonsend.ion.server.miscellaneous.NamespacedKeys
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.starlegacy.util.Tasks
import org.bukkit.craftbukkit.v1_19_R2.CraftWorld
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.WorldInitEvent
import org.bukkit.persistence.PersistentDataType
import java.util.Random
import java.util.concurrent.LinkedBlockingDeque
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.reflect.jvm.jvmName

object SpaceGenerationManager : Listener {
	val worldGenerators: MutableMap<ServerLevel, SpaceGenerator?> = mutableMapOf()
	val generationTasks = LinkedBlockingDeque<SpaceGenerationTask>()
	private val generationThreadPool = ThreadGroup("Space Generation")
	val chunkLock = mutableListOf<ChunkPos>()

	fun getGenerator(serverLevel: ServerLevel): SpaceGenerator? = worldGenerators[serverLevel]

	fun primeGeneration() {
		Tasks.syncRepeat(0, 10) { processQueue() }
	}

	private fun processQueue() {
		val task = generationTasks.take()
		Thread(generationThreadPool, task, task::class.jvmName)
	}

	@EventHandler
	fun onWorldInit(event: WorldInitEvent) {
		val serverLevel = (event.world as CraftWorld).handle

		Ion.configuration.spaceGenConfig[event.world.name]?.let { config ->
			worldGenerators[serverLevel] =
				SpaceGenerator(
					serverLevel,
					config
				)
		}
	}

	// Generate asteroids on chunk load
	@EventHandler
	fun onChunkLoad(event: ChunkLoadEvent) {
		val generator = getGenerator((event.world as CraftWorld).handle) ?: return
		if (event.chunk.persistentDataContainer.has(NamespacedKeys.SPACE_GEN_VERSION)) return

		event.chunk.persistentDataContainer.set(
			NamespacedKeys.SPACE_GEN_VERSION,
			PersistentDataType.BYTE,
			generator.spaceGenerationVersion
		)

		val worldX = event.chunk.x * 16
		val worldZ = event.chunk.z * 16

		val chunkDensity = generator.parseDensity(worldX.toDouble(), event.world.maxHeight / 2.0, worldZ.toDouble())

		val random = Random(System.currentTimeMillis() + event.world.seed + event.chunk.hashCode())

		// Generate a number of random asteroids in a chunk, proportional to the density in a portion of the chunk. Allows densities of X>1 asteroid per chunk.
		for (count in 0..ceil(chunkDensity).toInt()) {
			// random number out of 100, chance of asteroid's generation. For use in selection.
			val chance = random.nextDouble(100.0)

			// Selects some asteroids that are generated. Allows for densities of 0<X<1 asteroids per chunk.
			if (chance > (chunkDensity * 10)) continue

			// Random coordinate generation.
			val asteroidX = random.nextInt(0, 15) + worldX
			val asteroidZ = random.nextInt(0, 15) + worldZ
			val asteroidY = random.nextInt(event.world.minHeight + 10, event.world.maxHeight - 10)

			val asteroid = generator.generateWorldAsteroid(
				asteroidX,
				asteroidY,
				asteroidZ
			)

			if (asteroid.size + asteroidY > event.world.maxHeight) continue

			if (asteroidY - asteroid.size < event.world.minHeight) continue

			generationTasks.put(GenerateAsteroidTask(generator, asteroid))
		}

		for (count in 0..ceil(chunkDensity * generator.configuration.wreckMultiplier).roundToInt()) {
			// random number out of 100, chance of asteroid's generation. For use in selection.
			val chance = random.nextDouble(100.0)
			// Selects some wrecks that are generated. Allows for densities of 0<X<1 wrecks per chunk.
			if (chance > (chunkDensity * generator.configuration.wreckMultiplier * 10)) continue
			// Random coordinate generation.

			val wreckX = random.nextInt(0, 15) + worldX
			val wreckY = random.nextInt(event.world.minHeight + 10, event.world.maxHeight - 10)
			val wreckZ = random.nextInt(0, 15) + worldZ

			val wreck = generator.generateRandomWreckData(wreckX, wreckY, wreckZ)

			generationTasks.put(GenerateWreckTask(generator, wreck))
		}
	}
}

/**
 * Simple tagging class
 **/
abstract class SpaceGenerationTask : Runnable {
	abstract val generator: SpaceGenerator
}