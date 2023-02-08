package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.particle.Particles
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.arguments
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.effects.effects.particles.ParticleAnimations
import com.willfp.libreforge.effects.effects.particles.copy
import com.willfp.libreforge.effects.effects.particles.toDirectionVector
import com.willfp.libreforge.effects.effects.particles.toLocation
import com.willfp.libreforge.effects.effects.particles.toVector3f
import com.willfp.libreforge.getIntFromExpression
import com.willfp.libreforge.plugin
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import com.willfp.libreforge.triggers.Triggers
import org.bukkit.entity.LivingEntity

object EffectParticleAnimation : Effect<NoCompileData>("particle_animation") {
    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.LOCATION
    )

    override val arguments = arguments {
        require("particle", "You must specify the particle!")
        require("animation", "You must specify a valid animation!", Config::getString) {
            ParticleAnimations.getByID(it) != null
        }

        inherit("particle_args") { ParticleAnimations.getByID(it.getString("animation")) }
    }

    override fun onTrigger(config: Config, data: TriggerData, compileData: NoCompileData): Boolean {
        val location = data.location ?: return false
        val player = data.player ?: return false

        val entity = when (config.getString("entity").lowercase()) {
            "victim" -> data.victim
            "projectile" -> data.projectile
            else -> data.player
        } ?: return false

        val world = location.world ?: return false

        val animation = ParticleAnimations.getByID(config.getString("animation")) ?: return false
        val particle = Particles.lookup(config.getString("particle"))

        var tick = 0

        val args = config.getSubsection("particle_args")

        plugin.runnableFactory.create {
            val entityVector = if (config.getBool("use-eye-location") && entity is LivingEntity) {
                entity.eyeLocation.toVector3f()
            } else {
                entity.location.toVector3f()
            }

            val entityDirectionVector = entity.location.toDirectionVector()

            val locationVector = location.toVector3f()

            val vectors = if (args.has("tick-multiplier")) {
                val mult = args.getIntFromExpression("tick-multiplier", data)

                val mockTicks = (tick * mult until (tick * mult) + mult)

                mockTicks.map { t ->
                    animation.getParticleLocations(
                        t,
                        entityVector.copy(),
                        entityDirectionVector.copy(),
                        locationVector.copy(),
                        args,
                        player
                    )
                }.flatten()
            } else {
                animation.getParticleLocations(
                    tick,
                    entityVector.copy(),
                    entityDirectionVector.copy(),
                    locationVector.copy(),
                    args,
                    player
                )
            }

            for (vector in vectors) {
                particle.spawn(
                    vector.toLocation(world),
                    config.getIntFromExpression("particle-amount", data)
                )
            }

            if (vectors.any { v ->
                    animation.shouldStopTicking(
                        tick,
                        entityVector.copy(),
                        entityDirectionVector.copy(),
                        locationVector.copy(),
                        v,
                        args,
                        player
                    )
                }) {
                it.cancel()
            }

            tick++
        }.runTaskTimerAsynchronously(1, 1)

        return true
    }
}