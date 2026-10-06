package com.ailover.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ailover.app.ui.character.CharacterEditScreen
import com.ailover.app.ui.character.CharacterListScreen
import com.ailover.app.ui.chat.ChatScreen
import com.ailover.app.ui.conversation.ConversationListScreen

object Routes {
    const val CONVERSATION_LIST = "conversation_list"
    const val CHAT = "chat/{conversationId}/{title}"
    const val CHARACTER_LIST = "character_list"
    const val CHARACTER_EDIT = "character_edit/{characterId}"

    fun createChatRoute(conversationId: Long, title: String): String =
        "chat/$conversationId/$title"

    fun createCharacterEditRoute(characterId: Long): String =
        "character_edit/$characterId"
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.CONVERSATION_LIST
    ) {
        composable(Routes.CONVERSATION_LIST) {
            ConversationListScreen(
                onConversationClick = { conversationId, title ->
                    navController.navigate(Routes.createChatRoute(conversationId, title))
                },
                onCharacterManageClick = {
                    navController.navigate(Routes.CHARACTER_LIST)
                }
            )
        }

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

        composable(Routes.CHARACTER_LIST) {
            CharacterListScreen(
                onBackClick = { navController.popBackStack() },
                onAddClick = {
                    navController.navigate(Routes.createCharacterEditRoute(-1L))
                },
                onEditClick = { characterId ->
                    navController.navigate(Routes.createCharacterEditRoute(characterId))
                }
            )
        }

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
                onSaved = { navController.popBackStack() }
            )
        }
    }
}
