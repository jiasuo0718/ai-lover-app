import 'package:flutter/material.dart';
import '../services/local_storage_service.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  final _storage = LocalStorageService();

  Future<void> _clearAllData() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('清除全部数据'),
        content: const Text('这将删除所有角色、聊天记录和API配置，且无法恢复。确定继续吗？'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('取消')),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('清除', style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
    if (confirm == true) {
      await _storage.saveCharacters([]);
      await _storage.saveApis([]);
      await _storage.setActiveApiId(null);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已清除全部数据')));
        Navigator.pop(context);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('设置')),
      body: ListView(
        children: [
          const _SectionTitle('关于'),
          ListTile(
            leading: const Icon(Icons.info_outline),
            title: const Text('版本'),
            subtitle: const Text('1.0.0'),
          ),
          const Divider(),
          const _SectionTitle('数据管理'),
          ListTile(
            leading: const Icon(Icons.delete_forever, color: Colors.red),
            title: const Text('清除全部数据', style: TextStyle(color: Colors.red)),
            onTap: _clearAllData,
          ),
          const Divider(),
          const _SectionTitle('使用说明'),
          const Padding(
            padding: EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('1. 先在「API配置」中添加你的API Key', style: TextStyle(height: 1.8)),
                Text('2. 返回主页，点击「新建角色」创建AI角色', style: TextStyle(height: 1.8)),
                Text('3. 填写角色名称、性格、开场白等设定', style: TextStyle(height: 1.8)),
                Text('4. 点击角色进入聊天，长按消息可复制', style: TextStyle(height: 1.8)),
                Text('5. 左滑角色卡片可删除，长按可编辑', style: TextStyle(height: 1.8)),
                Text('6. 支持所有OpenAI兼容接口（DeepSeek、豆包方舟等）', style: TextStyle(height: 1.8)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  final String text;
  const _SectionTitle(this.text);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Text(text, style: TextStyle(fontSize: 13, color: Colors.grey[600], fontWeight: FontWeight.bold)),
    );
  }
}
