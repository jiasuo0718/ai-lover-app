import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/character.dart';
import '../models/chat_message.dart';
import '../models/api_config.dart';

class OpenAiApiService {
  final ApiConfig config;

  OpenAiApiService({required this.config});

  Future<String> chat({
    required Character character,
    required List<ChatMessage> history,
    required String userInput,
  }) async {
    final url = Uri.parse('${config.baseUrl.replaceAll(RegExp(r'/$'), '')}/chat/completions');

    final List<Map<String, String>> messages = [];

    // 系统提示词：角色设定
    String systemPrompt = '';
    if (character.personality.isNotEmpty) {
      systemPrompt += '你现在扮演的角色：${character.name}。\n';
      systemPrompt += '性格设定：${character.personality}\n';
    }
    if (character.scenario.isNotEmpty) {
      systemPrompt += '场景设定：${character.scenario}\n';
    }
    systemPrompt += '请始终以${character.name}的身份和语气回复，不要跳出角色。回复要自然、口语化，像真实聊天一样，不要使用markdown格式。';

    if (systemPrompt.isNotEmpty) {
      messages.add({'role': 'system', 'content': systemPrompt});
    }

    // 历史消息（只取最近20条，避免token爆炸）
    final recentHistory = history.length > 20
        ? history.sublist(history.length - 20)
        : history;
    for (final msg in recentHistory) {
      if (msg.role == 'user' || msg.role == 'assistant') {
        messages.add({'role': msg.role, 'content': msg.content});
      }
    }

    // 当前用户输入
    messages.add({'role': 'user', 'content': userInput});

    final body = jsonEncode({
      'model': character.model,
      'messages': messages,
      'temperature': character.temperature,
      'max_tokens': character.maxTokens,
    });

    final response = await http.post(
      url,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ${config.apiKey}',
      },
      body: body,
    );

    if (response.statusCode == 200) {
      final data = jsonDecode(utf8.decode(response.bodyBytes));
      final content = data['choices']?[0]?['message']?['content'] ?? '';
      return content.toString().trim();
    } else {
      throw Exception('API请求失败: ${response.statusCode} - ${response.body}');
    }
  }
}
