package net.starlegacy.feature.space

import net.minecraft.world.level.block.state.BlockState
import net.horizonsend.ion.common.database.Oid
import net.horizonsend.ion.common.database.schema.space.Star
import net.horizonsend.ion.server.miscellaneous.utils.Vec3i
import net.starlegacy.util.getSphereBlocks
import org.bukkit.Material
import org.bukkit.craftbukkit.v1_19_R3.util.CraftMagicNumbers

class CachedStar(
    val databaseId: Oid<Star>,
    override val name: String,
    spaceWorldName: String,
    location: Vec3i,
    private val material: Material,
    size: Double
) : CelestialBody(spaceWorldName, location),
	NamedCelestialBody {
	companion object {
		private const val MAX_SIZE = 100
	}

	init {
		require(size > 0 && size <= 1)
	}

	val sphereRadius = (MAX_SIZE * size).toInt()

	override fun createStructure(): Map<Vec3i, BlockState> {
		val blockData = CraftMagicNumbers.getBlock(material).defaultBlockState()

		return getSphereBlocks(sphereRadius).associateWith { blockData }
	}
}
