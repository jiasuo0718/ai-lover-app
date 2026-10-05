import 'package:flutter/material.dart';
import '../models/character.dart';
import '../models/api_config.dart';
import '../services/local_storage_service.dart';
import 'chat_page.dart';
import 'character_edit_page.dart';
import 'api_config_page.dart';
import 'settings_page.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final _storage = LocalStorageService();
  List<Character> _characters = [];
  List<ApiConfig> _apis = [];
  ApiConfig? _activeApi;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    final chars = await _storage.loadCharacters();
    final apis = await _storage.loadApis();
    final activeId = await _storage.getActiveApiId();
    setState(() {
      _characters = chars;
      _apis = apis;
      _activeApi = apis.where((e) => e.id == activeId).firstOrNull;
      _loading = false;
    });
  }

  Future<void> _addDefaultCharacter() async {
    final newChar = Character(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      name: '新角色',
      greeting: '你好呀，我是你的AI伴侣～',
      personality: '温柔、体贴、善解人意，喜欢关心你的日常生活。',
    );
    final result = await Navigator.push<Character>(
      context,
      MaterialPageRoute(
        builder: (_) => CharacterEditPage(character: newChar),
      ),
    );
    if (result != null) {
      setState(() => _characters.add(result));
      await _storage.saveCharacters(_characters);
    }
  }

  Future<void> _editCharacter(Character char, int index) async {
    final result = await Navigator.push<Character>(
      context,
      MaterialPageRoute(
        builder: (_) => CharacterEditPage(character: char),
      ),
    );
    if (result != null) {
      setState(() => _characters[index] = result);
      await _storage.saveCharacters(_characters);
    }
  }

  Future<void> _deleteCharacter(int index) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('删除角色'),
        content: Text('确定删除「${_characters[index].name}」吗？聊天记录也会一并删除。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('取消')),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('删除', style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
    if (confirm == true) {
      setState(() => _characters.removeAt(index));
      await _storage.saveCharacters(_characters);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('AI恋人', style: TextStyle(fontWeight: FontWeight.bold)),
        actions: [
          IconButton(
            icon: const Icon(Icons.api),
            tooltip: 'API配置',
            onPressed: () async {
              await Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const ApiConfigPage()),
              );
              _loadData();
            },
          ),
          IconButton(
            icon: const Icon(Icons.settings),
            tooltip: '设置',
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const SettingsPage()),
              );
            },
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _characters.isEmpty
              ? _buildEmptyState()
              : ListView.builder(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  itemCount: _characters.length,
                  itemBuilder: (context, index) {
                    final char = _characters[index];
                    return Dismissible(
                      key: Key(char.id),
                      direction: DismissDirection.endToStart,
                      background: Container(
                        color: Colors.red,
                        alignment: Alignment.centerRight,
                        padding: const EdgeInsets.only(right: 20),
                        child: const Icon(Icons.delete, color: Colors.white),
                      ),
                      onDismissed: (_) => _deleteCharacter(index),
                      child: ListTile(
                        leading: CircleAvatar(
                          radius: 28,
                          backgroundColor: Colors.green[100],
                          child: Text(
                            char.name.isNotEmpty ? char.name[0] : '?',
                            style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
                          ),
                        ),
                        title: Text(char.name, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 17)),
                        subtitle: Text(
                          char.greeting,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(color: Colors.grey[600], fontSize: 14),
                        ),
                        trailing: const Icon(Icons.chevron_right, color: Colors.grey),
                        onTap: () {
                          if (_activeApi == null) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('请先在右上角配置API')),
                            );
                            return;
                          }
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) => ChatPage(character: char, api: _activeApi!),
                            ),
                          );
                        },
                        onLongPress: () => _editCharacter(char, index),
                      ),
                    );
                  },
                ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _addDefaultCharacter,
        icon: const Icon(Icons.add),
        label: const Text('新建角色'),
        backgroundColor: Colors.green,
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.chat_bubble_outline, size: 80, color: Colors.grey[300]),
          const SizedBox(height: 16),
          Text('还没有角色', style: TextStyle(fontSize: 18, color: Colors.grey[600])),
          const SizedBox(height: 8),
          Text('点击下方按钮创建你的第一个AI角色', style: TextStyle(color: Colors.grey[500])),
          const SizedBox(height: 24),
          if (_activeApi == null)
            Card(
              margin: const EdgeInsets.symmetric(horizontal: 32),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  children: [
                    const Icon(Icons.warning_amber, color: Colors.orange),
                    const SizedBox(height: 8),
                    const Text('使用前请先配置API', style: TextStyle(fontWeight: FontWeight.bold)),
                    const SizedBox(height: 8),
                    ElevatedButton.icon(
                      onPressed: () async {
                        await Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => const ApiConfigPage()),
                        );
                        _loadData();
                      },
                      icon: const Icon(Icons.api),
                      label: const Text('去配置'),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}
