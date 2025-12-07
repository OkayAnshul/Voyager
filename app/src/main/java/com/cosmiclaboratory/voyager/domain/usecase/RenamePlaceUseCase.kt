package com.cosmiclaboratory.voyager.domain.usecase

import com.cosmiclaboratory.voyager.domain.model.Place
import com.cosmiclaboratory.voyager.domain.repository.PlaceRepository
import javax.inject.Inject

/**
 * Use case for renaming a place with a custom user-provided name
 *
 * This allows users to manually label places as "Home", "Work", "Gym", etc.
 * When a place is renamed, it sets isUserRenamed=true and updates the name.
 *
 * The enrichment logic (EnrichPlaceWithDetailsUseCase) will prioritize
 * user-renamed places above all other naming strategies.
 */
class RenamePlaceUseCase @Inject constructor(
    private val placeRepository: PlaceRepository
) {
    /**
     * Rename a place with a custom user-provided name
     *
     * @param placeId The ID of the place to rename
     * @param customName The custom name to apply (e.g., "Home", "My Office", "Favorite Gym")
     * @return The updated Place object
     */
    suspend operator fun invoke(placeId: Long, customName: String): Result<Place> {
        return try {
            // Get the current place
            val currentPlace = placeRepository.getPlaceById(placeId)
                ?: return Result.failure(Exception("Place not found"))

            // Validate custom name
            if (customName.isBlank()) {
                return Result.failure(Exception("Custom name cannot be blank"))
            }

            // Update place with custom name
            val updatedPlace = currentPlace.copy(
                name = customName.trim(),
                isUserRenamed = true
            )

            // Save to repository
            placeRepository.updatePlace(updatedPlace)

            Result.success(updatedPlace)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove custom name from a place (revert to automatic naming)
     *
     * @param placeId The ID of the place
     * @return The updated Place object with automatic naming
     */
    suspend fun removeCustomName(placeId: Long): Result<Place> {
        return try {
            val currentPlace = placeRepository.getPlaceById(placeId)
                ?: return Result.failure(Exception("Place not found"))

            // Revert to automatic naming
            val updatedPlace = currentPlace.copy(
                isUserRenamed = false,
                // Keep the name as it might be useful, but mark as not user-renamed
                // The enrichment logic will handle regenerating the name
            )

            placeRepository.updatePlace(updatedPlace)

            Result.success(updatedPlace)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
