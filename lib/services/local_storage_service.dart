import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/character.dart';
import '../models/chat_message.dart';
import '../models/api_config.dart';

class LocalStorageService {
  static const _keyCharacters = 'characters';
  static const _keyMessages = 'messages_';
  static const _keyApis = 'api_configs';
  static const _keyActiveApi = 'active_api_id';

  Future<List<Character>> loadCharacters() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_keyCharacters);
    if (raw == null || raw.isEmpty) return [];
    final List<dynamic> list = jsonDecode(raw);
    return list.map((e) => Character.fromJson(e)).toList();
  }

  Future<void> saveCharacters(List<Character> list) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
        _keyCharacters, jsonEncode(list.map((e) => e.toJson()).toList()));
  }

  Future<List<ChatMessage>> loadMessages(String characterId) async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString('$_keyMessages$characterId');
    if (raw == null || raw.isEmpty) return [];
    final List<dynamic> list = jsonDecode(raw);
    return list.map((e) => ChatMessage.fromJson(e)).toList();
  }

  Future<void> saveMessages(
      String characterId, List<ChatMessage> list) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('$_keyMessages$characterId',
        jsonEncode(list.map((e) => e.toJson()).toList()));
  }

  Future<List<ApiConfig>> loadApis() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_keyApis);
    if (raw == null || raw.isEmpty) return [];
    final List<dynamic> list = jsonDecode(raw);
    return list.map((e) => ApiConfig.fromJson(e)).toList();
  }

  Future<void> saveApis(List<ApiConfig> list) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
        _keyApis, jsonEncode(list.map((e) => e.toJson()).toList()));
  }

  Future<String?> getActiveApiId() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_keyActiveApi);
  }

  Future<void> setActiveApiId(String? id) async {
    final prefs = await SharedPreferences.getInstance();
    if (id == null) {
      await prefs.remove(_keyActiveApi);
    } else {
      await prefs.setString(_keyActiveApi, id);
    }
  }
}
