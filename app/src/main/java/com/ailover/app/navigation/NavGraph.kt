package com.ailover.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ailover.app.ui.character.CharacterEditScreen
import com.ailover.app.ui.character.CharacterListScreen
import com.ailover.app.ui.chat.ChatScreen
import com.ailover.app.ui.conversation.ConversationListScreen
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

    fun createChatRoute(conversationId: Long, title: String): String =
        "chat/$conversationId/$title"

    fun createCharacterEditRoute(characterId: Long): String =
        "character_edit/$characterId"

    // 顶层页面（显示底部导航）
    val topLevelRoutes = setOf(CONVERSATION_LIST, CONTACTS, SETTINGS)
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.CONVERSATION_LIST, "聊天", Icons.Filled.ChatBubble),
    BottomNavItem(Routes.CONTACTS, "通讯录", Icons.Filled.Group),
    BottomNavItem(Routes.SETTINGS, "我", Icons.Filled.Person)
)

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in Routes.topLevelRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Box {
                    NavigationBar(
                        containerColor = CardWhite,
                        tonalElevation = 0.dp
                    ) {
                        bottomNavItems.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.route,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(Routes.CONVERSATION_LIST) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        fontSize = 11.sp
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = TextPrimary,
                                    selectedTextColor = TextPrimary,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = CardWhite
                                )
                            )
                        }
                    }
                    // 顶部分割线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Divider)
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.CONVERSATION_LIST,
            modifier = Modifier
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
                    onEditClick = { characterId ->
                        navController.navigate(Routes.createCharacterEditRoute(characterId))
                    }
                )
            }

            // 我（设置）
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }

            // ========== 二级页面（不显示底部导航） ==========

            // 聊天页
            composable(
                route = Routes.CHAT,
                arguments = listOf(
                    navArgument("conversationId") { type = NavType.LongType },
                    navArgument("title") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val conversationId = backStackEntry.arguments?.getLong("conversationId") ?: 0L
                val title = backStackEntry.arguments?.getString("title") ?: "聊天"
                ChatScreen(
                    conversationId = conversationId,
                    title = title,
                    onBackClick = { navController.popBackStack() }
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
                        // 保存后跳到通讯录 tab
                        navController.navigate(Routes.CONTACTS) {
                            popUpTo(Routes.CONVERSATION_LIST) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}
