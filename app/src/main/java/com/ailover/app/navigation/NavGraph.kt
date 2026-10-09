package com.ailover.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import io.github.qdsfdhvh.iconpark.IconParkIcons
import io.github.qdsfdhvh.iconpark.outline.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ailover.app.ui.character.CharacterDetailScreen
import com.ailover.app.ui.character.CharacterEditScreen
import com.ailover.app.ui.character.CharacterListScreen
import com.ailover.app.ui.character.CharacterSettingsScreen
import com.ailover.app.ui.chat.ChatScreen
import com.ailover.app.ui.conversation.ConversationListScreen
import com.ailover.app.ui.profile.ProfileScreen
import com.ailover.app.ui.profile.UserProfileEditScreen
import com.ailover.app.ui.settings.ApiProfileEditScreen
import com.ailover.app.ui.settings.ApiProfileListScreen
import com.ailover.app.ui.settings.SettingsScreen
import com.ailover.app.ui.theme.CardWhite
import com.ailover.app.ui.theme.Divider
import com.ailover.app.ui.theme.TextPrimary
import com.ailover.app.ui.theme.TextSecondary

object Routes {
    const val CONVERSATION_LIST = "conversation_list"
    const val CONTACTS = "contacts"
    const val SETTINGS = "settings"
    const val CHAT = "chat/{conversationId}/{title}"
    const val CHARACTER_EDIT = "character_edit/{characterId}"
    const val CHARACTER_DETAIL = "character_detail/{characterId}"
    const val CHARACTER_SETTINGS = "character_settings/{characterId}"
    const val USER_PROFILE_EDIT = "user_profile_edit"
    const val API_SETTINGS = "api_settings"
    const val API_PROFILE_LIST = "api_profile_list"
    const val API_PROFILE_EDIT = "api_profile_edit/{profileId}"

    fun createApiProfileEditRoute(profileId: String): String =
        "api_profile_edit/$profileId"

    fun createChatRoute(conversationId: Long, title: String): String =
        "chat/$conversationId/$title"

    fun createCharacterEditRoute(characterId: Long): String =
        "character_edit/$characterId"

    fun createCharacterDetailRoute(characterId: Long): String =
        "character_detail/$characterId"

    fun createCharacterSettingsRoute(characterId: Long): String =
        "character_settings/$characterId"

    // 顶层页面（显示底部导航）
    val topLevelRoutes = setOf(CONVERSATION_LIST, CONTACTS, SETTINGS)
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.CONVERSATION_LIST, "聊天", IconParkIcons.Outline.Comment),
    BottomNavItem(Routes.CONTACTS, "通讯录", IconParkIcons.Outline.PersonalCollection),
    BottomNavItem(Routes.SETTINGS, "我", IconParkIcons.Outline.People)
)

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in Routes.topLevelRoutes

    // 切换 tab：弹出所有二级页面，只保留顶层
    val navigateToTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(Routes.CONVERSATION_LIST)
            launchSingleTop = true
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Column {
                    // 顶部分割线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Divider)
                    )
                    // 自定义底部导航（无 indicator、无 ripple）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(CardWhite),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bottomNavItems.forEach { item ->
                            val isSelected = currentRoute == item.route
                            val interactionSource = remember { MutableInteractionSource() }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        onClick = { navigateToTopLevel(item.route) }
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = if (isSelected) TextPrimary else TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.label,
                                    fontSize = 11.sp,
                                    color = if (isSelected) TextPrimary else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.CONVERSATION_LIST,
            modifier = Modifier,
            // 默认：所有页面切换无动画，直接出现
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            // ========== 顶层页面 ==========

            // 聊天（会话列表）
            composable(Routes.CONVERSATION_LIST) {
                ConversationListScreen(
                    onConversationClick = { conversationId, title ->
                        navController.navigate(Routes.createChatRoute(conversationId, title))
                    },
                    onAddCharacterClick = {
                        navController.navigate(Routes.createCharacterEditRoute(-1L))
                    }
                )
            }

            // 通讯录（角色列表）
            composable(Routes.CONTACTS) {
                CharacterListScreen(
                    onAddClick = {
                        navController.navigate(Routes.createCharacterEditRoute(-1L))
                    },
                    onCharacterClick = { characterId ->
                        navController.navigate(Routes.createCharacterDetailRoute(characterId))
                    }
                )
            }

            // 我（个人资料）
            composable(Routes.SETTINGS) {
                ProfileScreen(
                    onEditProfileClick = {
                        navController.navigate(Routes.USER_PROFILE_EDIT)
                    },
                    onApiSettingsClick = {
                        navController.navigate(Routes.API_SETTINGS)
                    }
                )
            }

            // ========== 二级页面（不显示底部导航） ==========

            // 聊天页（唯一保留滑入动画的页面）
            composable(
                route = Routes.CHAT,
                arguments = listOf(
                    navArgument("conversationId") { type = NavType.LongType },
                    navArgument("title") { type = NavType.StringType }
                ),
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it / 4 },
                        animationSpec = tween(150)
                    ) + fadeIn(animationSpec = tween(150))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(100))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(150))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it / 4 },
                        animationSpec = tween(150)
                    ) + fadeOut(animationSpec = tween(150))
                }
            ) { backStackEntry ->
                val conversationId = backStackEntry.arguments?.getLong("conversationId") ?: 0L
                val title = backStackEntry.arguments?.getString("title") ?: "聊天"
                ChatScreen(
                    conversationId = conversationId,
                    title = title,
                    onBackClick = { navController.popBackStack() },
                    onSettingsClick = { cid ->
                        navController.navigate(Routes.createCharacterSettingsRoute(cid))
                    }
                )
            }

            // 角色资料页
            composable(
                route = Routes.CHARACTER_DETAIL,
                arguments = listOf(
                    navArgument("characterId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val characterId = backStackEntry.arguments?.getLong("characterId") ?: 0L
                CharacterDetailScreen(
                    characterId = characterId,
                    onBackClick = { navController.popBackStack() },
                    onEditClick = {
                        navController.navigate(Routes.createCharacterEditRoute(characterId))
                    },
                    onStartChat = { conversationId, title ->
                        navController.navigate(Routes.createChatRoute(conversationId, title))
                    },
                    onDeleted = {
                        // 删除后回通讯录 tab
                        navigateToTopLevel(Routes.CONTACTS)
                    },
                    onSettingsClick = {
                        navController.navigate(Routes.createCharacterSettingsRoute(characterId))
                    }
                )
            }

            // 角色编辑页
            composable(
                route = Routes.CHARACTER_EDIT,
                arguments = listOf(
                    navArgument("characterId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val characterId = backStackEntry.arguments?.getLong("characterId") ?: -1L
                val effectiveId = if (characterId == -1L) null else characterId
                CharacterEditScreen(
                    characterId = effectiveId,
                    onBackClick = { navController.popBackStack() },
                    onSaved = {
                        // 保存后：先弹出编辑页，再切到通讯录 tab
                        navController.popBackStack()
                        navigateToTopLevel(Routes.CONTACTS)
                    }
                )
            }

            // 角色设置页
            composable(
                route = Routes.CHARACTER_SETTINGS,
                arguments = listOf(
                    navArgument("characterId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val characterId = backStackEntry.arguments?.getLong("characterId") ?: 0L
                CharacterSettingsScreen(
                    characterId = characterId,
                    onBackClick = { navController.popBackStack() },
                    onEditClick = { cid ->
                        navController.navigate(Routes.createCharacterEditRoute(cid))
                    },
                    onDeleted = {
                        // 删除后回通讯录 tab
                        navigateToTopLevel(Routes.CONTACTS)
                    },
                    onApiProfileListClick = {
                        navController.navigate(Routes.API_PROFILE_LIST)
                    }
                )
            }

            // 编辑个人信息页
            composable(Routes.USER_PROFILE_EDIT) {
                UserProfileEditScreen(
                    onBackClick = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }

            // API 设置页
            composable(Routes.API_SETTINGS) {
                SettingsScreen(
                    onApiProfileListClick = {
                        navController.navigate(Routes.API_PROFILE_LIST)
                    }
                )
            }

            // API 配置列表页
            composable(Routes.API_PROFILE_LIST) {
                ApiProfileListScreen(
                    onBackClick = { navController.popBackStack() },
                    onAddClick = { navController.navigate(Routes.createApiProfileEditRoute("new")) },
                    onEditClick = { id -> navController.navigate(Routes.createApiProfileEditRoute(id)) }
                )
            }

            // API 配置编辑页
            composable(
                route = Routes.API_PROFILE_EDIT,
                arguments = listOf(
                    navArgument("profileId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getString("profileId")
                val effectiveId = if (profileId == "new") null else profileId
                ApiProfileEditScreen(
                    profileId = effectiveId,
                    onBackClick = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
        }
    }
}
