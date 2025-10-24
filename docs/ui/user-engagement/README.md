# 🎮 User Engagement & Gamification

## Overview
This document outlines user engagement strategies and gamification elements designed to encourage consistent app usage, location tracking, and user retention while maintaining privacy and providing genuine value.

## Gamification Philosophy

### Core Principles
1. **Intrinsic Motivation**: Encourage genuine exploration and self-discovery
2. **Privacy First**: All achievements respect user privacy preferences
3. **Meaningful Progress**: Rewards should reflect real accomplishments
4. **Inclusive Design**: Accessible to users with different mobility levels
5. **Optional Participation**: Users can disable gamification features

### Value Proposition
- **Personal Growth**: Track and celebrate lifestyle improvements
- **Discovery**: Encourage exploration of new places and experiences
- **Habits**: Support healthy location tracking habits
- **Insights**: Gamify the process of understanding personal patterns

---

## Achievement System

### Achievement Categories
```kotlin
sealed class AchievementType(
    val category: AchievementCategory,
    val difficulty: AchievementDifficulty,
    val points: Int
) {
    // Distance Achievements
    object FirstKilometer : AchievementType(EXPLORATION, EASY, 10)
    object MarathonWalker : AchievementType(EXPLORATION, HARD, 100)
    object GlobalTraveler : AchievementType(EXPLORATION, LEGENDARY, 500)
    
    // Place Discovery
    object FirstPlace : AchievementType(DISCOVERY, EASY, 15)
    object PlaceCollector : AchievementType(DISCOVERY, MEDIUM, 50)
    object CategoryMaster : AchievementType(DISCOVERY, HARD, 200)
    
    // Consistency
    object WeekStreak : AchievementType(CONSISTENCY, EASY, 25)
    object MonthlyChampion : AchievementType(CONSISTENCY, MEDIUM, 75)
    object YearlyDedicated : AchievementType(CONSISTENCY, LEGENDARY, 365)
    
    // Special Achievements
    object EarlyBird : AchievementType(SPECIAL, MEDIUM, 40)
    object NightOwl : AchievementType(SPECIAL, MEDIUM, 40)
    object WeekendExplorer : AchievementType(SPECIAL, EASY, 30)
}

enum class AchievementCategory(val displayName: String, val icon: ImageVector, val color: Color) {
    EXPLORATION("Exploration", Icons.Default.Explore, Color(0xFF4CAF50)),
    DISCOVERY("Discovery", Icons.Default.Search, Color(0xFFFF9800)),
    CONSISTENCY("Consistency", Icons.Default.CalendarToday, Color(0xFF2196F3)),
    SPECIAL("Special", Icons.Default.Star, Color(0xFFFFD700)),
    SOCIAL("Social", Icons.Default.Group, Color(0xFF9C27B0)),
    SEASONAL("Seasonal", Icons.Default.Event, Color(0xFFE91E63))
}

enum class AchievementDifficulty(val multiplier: Float, val color: Color) {
    EASY(1.0f, Color(0xFF4CAF50)),
    MEDIUM(2.0f, Color(0xFFFF9800)),
    HARD(3.0f, Color(0xFFFF5722)),
    LEGENDARY(5.0f, Color(0xFF9C27B0))
}
```

### Achievement Data Model
```kotlin
data class Achievement(
    val id: String,
    val type: AchievementType,
    val title: String,
    val description: String,
    val iconUrl: String? = null,
    val progress: AchievementProgress,
    val isUnlocked: Boolean = false,
    val unlockedAt: LocalDateTime? = null,
    val nextLevel: Achievement? = null,
    val requirements: AchievementRequirements
)

data class AchievementProgress(
    val current: Int,
    val target: Int,
    val percentage: Float = current.toFloat() / target.coerceAtLeast(1)
)

data class AchievementRequirements(
    val minimumDays: Int? = null,
    val minimumDistance: Double? = null,
    val minimumPlaces: Int? = null,
    val specificCategory: PlaceCategory? = null,
    val timeOfDay: TimeRange? = null,
    val dayOfWeek: DayOfWeek? = null
)

data class TimeRange(
    val start: LocalTime,
    val end: LocalTime
)
```

### Achievement UI Components
```kotlin
@Composable
fun AchievementCard(
    achievement: Achievement,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (achievement.isUnlocked) {
                achievement.type.category.color.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AchievementIcon(
                achievement = achievement,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = achievement.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    DifficultyBadge(difficulty = achievement.type.difficulty)
                }
                
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (!achievement.isUnlocked) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AchievementProgressBar(progress = achievement.progress)
                }
            }
            
            if (achievement.isUnlocked) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Unlocked",
                        tint = achievement.type.category.color
                    )
                    Text(
                        text = "+${achievement.type.points}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = achievement.type.category.color
                    )
                }
            }
        }
    }
}

@Composable
fun AchievementIcon(
    achievement: Achievement,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val alpha = if (achievement.isUnlocked) 1.0f else 0.4f
        
        Surface(
            shape = CircleShape,
            color = achievement.type.category.color.copy(alpha = alpha),
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = achievement.type.category.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.padding(12.dp)
            )
        }
        
        if (!achievement.isUnlocked) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun AchievementProgressBar(
    progress: AchievementProgress,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${progress.current}/${progress.target}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "${(progress.percentage * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        LinearProgressIndicator(
            progress = progress.percentage,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```

---

## Streak System

### Streak Types
```kotlin
sealed class StreakType(val name: String, val description: String) {
    object Daily : StreakType("Daily Tracking", "Track locations every day")
    object Weekly : StreakType("Weekly Explorer", "Visit new places each week")
    object PlaceVisit : StreakType("Place Regular", "Visit the same place consistently")
    object Exercise : StreakType("Active Lifestyle", "Maintain movement goals")
}

data class Streak(
    val type: StreakType,
    val currentStreak: Int,
    val longestStreak: Int,
    val lastActivity: LocalDate,
    val isActive: Boolean,
    val milestones: List<StreakMilestone>
)

data class StreakMilestone(
    val days: Int,
    val title: String,
    val reward: String,
    val isUnlocked: Boolean
)

@Composable
fun StreakCard(
    streak: Streak,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (streak.isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = streak.type.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = streak.type.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                StreakFlame(
                    isActive = streak.isActive,
                    streakCount = streak.currentStreak
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StreakStat(
                    label = "Current",
                    value = "${streak.currentStreak} days"
                )
                StreakStat(
                    label = "Longest",
                    value = "${streak.longestStreak} days"
                )
                StreakStat(
                    label = "Last Activity",
                    value = formatRelativeDate(streak.lastActivity)
                )
            }
            
            if (streak.milestones.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                StreakMilestones(milestones = streak.milestones)
            }
        }
    }
}

@Composable
fun StreakFlame(
    isActive: Boolean,
    streakCount: Int,
    modifier: Modifier = Modifier
) {
    val flameColor = when {
        !isActive -> Color.Gray
        streakCount >= 30 -> Color(0xFFFF6B35) // Orange-red
        streakCount >= 7 -> Color(0xFFFF9500) // Orange
        else -> Color(0xFFFFD60A) // Yellow
    }
    
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.2f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.scale(scale)
    ) {
        Icon(
            imageVector = Icons.Default.Whatshot,
            contentDescription = "Streak flame",
            tint = flameColor,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = "$streakCount",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = flameColor
        )
    }
}
```

---

## Progress Tracking

### Goal System
```kotlin
data class Goal(
    val id: String,
    val type: GoalType,
    val title: String,
    val description: String,
    val target: GoalTarget,
    val progress: GoalProgress,
    val deadline: LocalDate?,
    val isCompleted: Boolean = false,
    val completedAt: LocalDateTime? = null,
    val reward: String? = null
)

sealed class GoalType(val name: String, val icon: ImageVector) {
    object Distance : GoalType("Distance", Icons.Default.DirectionsWalk)
    object Places : GoalType("Places", Icons.Default.Place)
    object Categories : GoalType("Categories", Icons.Default.Category)
    object Visits : GoalType("Visits", Icons.Default.Event)
    object Streak : GoalType("Streak", Icons.Default.Whatshot)
}

data class GoalTarget(
    val value: Double,
    val unit: String,
    val timeFrame: TimeFrame
)

enum class TimeFrame(val displayName: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly"),
    CUSTOM("Custom")
}

data class GoalProgress(
    val current: Double,
    val target: Double,
    val percentage: Float = (current / target.coerceAtLeast(0.1)).coerceAtMost(1.0).toFloat(),
    val isOnTrack: Boolean,
    val projectedCompletion: LocalDate?
)

@Composable
fun GoalProgressCard(
    goal: Goal,
    onEditGoal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (goal.isCompleted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = goal.type.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                IconButton(onClick = onEditGoal) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit goal")
                }
            }
            
            Text(
                text = goal.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress visualization
            CircularProgressWithStats(
                progress = goal.progress,
                isCompleted = goal.isCompleted
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Deadline and status
            GoalStatusRow(goal = goal)
        }
    }
}

@Composable
fun CircularProgressWithStats(
    progress: GoalProgress,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circular progress indicator
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(120.dp)
        ) {
            CircularProgressIndicator(
                progress = progress.percentage,
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 8.dp,
                color = if (isCompleted) {
                    MaterialTheme.colorScheme.primary
                } else if (progress.isOnTrack) {
                    Color.Green
                } else {
                    Color.Orange
                }
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${(progress.percentage * 100).toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        
        // Progress stats
        Column {
            ProgressStat(
                label = "Current",
                value = formatValue(progress.current),
                icon = Icons.Default.TrendingUp
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProgressStat(
                label = "Target", 
                value = formatValue(progress.target),
                icon = Icons.Default.Flag
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProgressStat(
                label = "Remaining",
                value = formatValue(progress.target - progress.current),
                icon = Icons.Default.Schedule
            )
        }
    }
}
```

---

## Notification & Celebrations

### Achievement Notifications
```kotlin
@Composable
fun AchievementUnlockedDialog(
    achievement: Achievement,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Celebration animation
                AchievementCelebration()
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Achievement details
                AchievementIcon(
                    achievement = achievement,
                    modifier = Modifier.size(80.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Achievement Unlocked!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = achievement.type.category.color,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Continue")
                    }
                    
                    Button(
                        onClick = onShare,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = achievement.type.category.color
                        )
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share")
                    }
                }
            }
        }
    }
}

@Composable
fun AchievementCelebration() {
    val infiniteTransition = rememberInfiniteTransition()
    
    val sparkleScale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // Sparkle effects
        repeat(8) { index ->
            val angle = (index * 45f)
            val scale = sparkleScale * (0.5f + index * 0.1f)
            
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFFFD700),
                modifier = Modifier
                    .offset(
                        x = (30 * cos(Math.toRadians(angle.toDouble()))).dp,
                        y = (30 * sin(Math.toRadians(angle.toDouble()))).dp
                    )
                    .scale(scale)
                    .rotate(angle)
            )
        }
    }
}
```

### Smart Notifications
```kotlin
class EngagementNotificationManager(
    private val notificationManager: NotificationManager,
    private val achievementRepository: AchievementRepository,
    private val streakRepository: StreakRepository
) {
    companion object {
        private const val ACHIEVEMENT_CHANNEL_ID = "achievements"
        private const val STREAK_CHANNEL_ID = "streaks"
        private const val GOAL_CHANNEL_ID = "goals"
    }
    
    suspend fun sendAchievementNotification(achievement: Achievement) {
        val notification = NotificationCompat.Builder(context, ACHIEVEMENT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_achievement)
            .setContentTitle("Achievement Unlocked!")
            .setContentText(achievement.title)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${achievement.title}\n${achievement.description}"))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                R.drawable.ic_share,
                "Share",
                createSharePendingIntent(achievement)
            )
            .build()
        
        notificationManager.notify(achievement.id.hashCode(), notification)
    }
    
    suspend fun sendStreakNotification(streak: Streak) {
        if (streak.currentStreak % 7 == 0 && streak.currentStreak > 0) {
            val notification = NotificationCompat.Builder(context, STREAK_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_streak)
                .setContentTitle("Streak Milestone!")
                .setContentText("${streak.currentStreak} day ${streak.type.name} streak! 🔥")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            
            notificationManager.notify(streak.type.hashCode(), notification)
        }
    }
    
    suspend fun sendGoalProgressNotification(goal: Goal) {
        if (goal.progress.percentage >= 0.75f && !goal.isCompleted) {
            val notification = NotificationCompat.Builder(context, GOAL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_goal)
                .setContentTitle("Almost there!")
                .setContentText("You're ${(goal.progress.percentage * 100).toInt()}% of the way to completing '${goal.title}'")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            
            notificationManager.notify(goal.id.hashCode(), notification)
        }
    }
}
```

---

## Social Features

### Sharing Achievements
```kotlin
@Composable
fun ShareAchievementCard(
    achievement: Achievement,
    userStats: UserStats,
    onShare: (SharePlatform) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = achievement.type.category.color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App branding
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Voyager",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Achievement showcase
            AchievementIcon(
                achievement = achievement,
                modifier = Modifier.size(80.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = achievement.description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // User stats summary
            ShareableStats(userStats = userStats)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Share buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SharePlatform.values().forEach { platform ->
                    ShareButton(
                        platform = platform,
                        onClick = { onShare(platform) }
                    )
                }
            }
        }
    }
}

enum class SharePlatform(val displayName: String, val icon: ImageVector) {
    TWITTER("Twitter", Icons.Default.Share),
    INSTAGRAM("Instagram", Icons.Default.CameraAlt),
    FACEBOOK("Facebook", Icons.Default.Group),
    GENERIC("Share", Icons.Default.Share)
}

@Composable
fun ShareButton(
    platform: SharePlatform,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .background(
                MaterialTheme.colorScheme.primaryContainer,
                CircleShape
            )
    ) {
        Icon(
            imageVector = platform.icon,
            contentDescription = "Share on ${platform.displayName}",
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
```

---

## Privacy & Ethics

### Privacy-First Design
```kotlin
data class EngagementPreferences(
    val enableAchievements: Boolean = true,
    val enableStreaks: Boolean = true,
    val enableGoals: Boolean = true,
    val enableNotifications: Boolean = true,
    val enableSharing: Boolean = false,
    val privacyLevel: PrivacyLevel = PrivacyLevel.PERSONAL
)

enum class PrivacyLevel {
    PERSONAL, // Only user sees achievements
    FRIENDS,  // Share with selected friends
    PUBLIC    // Public achievements (anonymized)
}

class PrivacyManager {
    fun filterAchievementsForSharing(
        achievements: List<Achievement>,
        privacyLevel: PrivacyLevel
    ): List<Achievement> {
        return when (privacyLevel) {
            PrivacyLevel.PERSONAL -> emptyList()
            PrivacyLevel.FRIENDS -> achievements.filter { it.isSharable }
            PrivacyLevel.PUBLIC -> achievements.filter { 
                it.isSharable && !it.containsLocationData 
            }
        }
    }
    
    fun anonymizeAchievement(achievement: Achievement): Achievement {
        return achievement.copy(
            description = achievement.description.anonymizeLocationReferences()
        )
    }
}
```

### Ethical Guidelines
1. **No Dark Patterns**: Achievements encourage genuine exploration
2. **Respect Limits**: Users can disable any gamification features
3. **Inclusive Design**: Achievements accessible to all mobility levels
4. **Privacy Protection**: Location data never shared without explicit consent
5. **Meaningful Progress**: Rewards reflect real accomplishments, not just app usage

---

## Analytics & Insights

### Engagement Metrics
```kotlin
data class EngagementMetrics(
    val achievementsUnlocked: Int,
    val longestStreak: Int,
    val goalsCompleted: Int,
    val totalPoints: Int,
    val engagementScore: Float,
    val timeToFirstAchievement: Duration?,
    val averageSessionLength: Duration,
    val retentionRate: Float
)

class EngagementAnalytics {
    fun calculateEngagementScore(
        user: User,
        timeframe: TimeFrame
    ): Float {
        val factors = listOf(
            user.achievementsUnlocked * 0.3f,
            user.longestStreak * 0.2f,
            user.goalsCompleted * 0.25f,
            user.consistencyScore * 0.25f
        )
        
        return factors.sum().coerceIn(0f, 100f)
    }
    
    fun generatePersonalizedRecommendations(
        user: User,
        engagementMetrics: EngagementMetrics
    ): List<Recommendation> {
        // AI-powered recommendations based on user patterns
        return buildList {
            if (user.longestStreak < 7) {
                add(Recommendation.BuildStreak)
            }
            
            if (user.visitedCategories.size < 5) {
                add(Recommendation.ExploreNewCategories)
            }
            
            // More personalized suggestions...
        }
    }
}
```

This comprehensive user engagement system creates meaningful motivation for users while respecting their privacy and providing genuine value through location tracking insights.