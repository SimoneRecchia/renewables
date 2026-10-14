package io.github.simonerecchia.renewables.grid;

/**
 * What a wind generator tells the grid so that the grid can decide whether it is putting a disturbance on the line.
 *
 * @param meanSpeed  the ten-minute mean wind at the hub, in m/s
 * @param turbulence turbulence intensity, the standard deviation of the wind over its mean
 */
public record WindSample(double meanSpeed, double turbulence) {
}
