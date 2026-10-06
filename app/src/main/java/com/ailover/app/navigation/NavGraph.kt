package com.ailover.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ailover.app.ui.chat.ChatScreen
import com.ailover.app.ui.conversation.ConversationListScreen

object Routes {
    const val CONVERSATION_LIST = "conversation_list"
    const val CHAT = "chat/{conversationId}/{title}"

    fun createChatRoute(conversationId: Long, title: String): String {
        return "chat/$conversationId/$title"
    }
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
    }
}
