import 'package:flutter/material.dart';
import '../models/api_config.dart';
import '../services/local_storage_service.dart';

class ApiConfigPage extends StatefulWidget {
  const ApiConfigPage({super.key});

  @override
  State<ApiConfigPage> createState() => _ApiConfigPageState();
}

class _ApiConfigPageState extends State<ApiConfigPage> {
  final _storage = LocalStorageService();
  List<ApiConfig> _apis = [];
  String? _activeId;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final apis = await _storage.loadApis();
    final activeId = await _storage.getActiveApiId();
    setState(() {
      _apis = apis;
      _activeId = activeId;
      _loading = false;
    });
  }

  Future<void> _addApi() async {
    final result = await showDialog<ApiConfig>(
      context: context,
      builder: (_) => _ApiEditDialog(config: null),
    );
    if (result != null) {
      setState(() => _apis.add(result));
      await _storage.saveApis(_apis);
      if (_activeId == null) {
        _activeId = result.id;
        await _storage.setActiveApiId(_activeId);
      }
    }
  }

  Future<void> _editApi(ApiConfig config, int index) async {
    final result = await showDialog<ApiConfig>(
      context: context,
      builder: (_) => _ApiEditDialog(config: config),
    );
    if (result != null) {
      setState(() => _apis[index] = result);
      await _storage.saveApis(_apis);
    }
  }

  Future<void> _deleteApi(int index) async {
    final id = _apis[index].id;
    setState(() => _apis.removeAt(index));
    await _storage.saveApis(_apis);
    if (_activeId == id) {
      _activeId = _apis.isNotEmpty ? _apis.first.id : null;
      await _storage.setActiveApiId(_activeId);
    }
  }

  Future<void> _setActive(String id) async {
    setState(() => _activeId = id);
    await _storage.setActiveApiId(id);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('API配置'),
        actions: [
          IconButton(icon: const Icon(Icons.add), onPressed: _addApi),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _apis.isEmpty
              ? _buildEmpty()
              : ListView.builder(
                  padding: const EdgeInsets.all(12),
                  itemCount: _apis.length,
                  itemBuilder: (context, index) {
                    final api = _apis[index];
                    final isActive = api.id == _activeId;
                    return Card(
                      child: ListTile(
                        leading: Radio<String>(
                          value: api.id,
                          groupValue: _activeId,
                          onChanged: (v) => _setActive(v!),
                        ),
                        title: Text(api.name, style: const TextStyle(fontWeight: FontWeight.bold)),
                        subtitle: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(api.baseUrl, style: TextStyle(fontSize: 12, color: Colors.grey[600])),
                            Text('默认模型: ${api.defaultModel}', style: TextStyle(fontSize: 12, color: Colors.grey[500])),
                          ],
                        ),
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            if (isActive)
                              const Chip(label: Text('使用中', style: TextStyle(fontSize: 11)), backgroundColor: Colors.green, labelStyle: TextStyle(color: Colors.white)),
                            IconButton(icon: const Icon(Icons.edit, size: 20), onPressed: () => _editApi(api, index)),
                            IconButton(icon: const Icon(Icons.delete, size: 20, color: Colors.red), onPressed: () => _deleteApi(index)),
                          ],
                        ),
                        onTap: () => _setActive(api.id),
                      ),
                    );
                  },
                ),
    );
  }

  Widget _buildEmpty() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.api, size: 70, color: Colors.grey[300]),
          const SizedBox(height: 16),
          const Text('还没有配置API', style: TextStyle(fontSize: 18, color: Colors.grey)),
          const SizedBox(height: 8),
          const Text('支持所有OpenAI兼容接口', style: TextStyle(color: Colors.grey)),
          const SizedBox(height: 20),
          ElevatedButton.icon(
            onPressed: _addApi,
            icon: const Icon(Icons.add),
            label: const Text('添加API'),
          ),
        ],
      ),
    );
  }
}

class _ApiEditDialog extends StatefulWidget {
  final ApiConfig? config;
  const _ApiEditDialog({this.config});

  @override
  State<_ApiEditDialog> createState() => _ApiEditDialogState();
}

class _ApiEditDialogState extends State<_ApiEditDialog> {
  late TextEditingController _nameCtrl;
  late TextEditingController _urlCtrl;
  late TextEditingController _keyCtrl;
  late TextEditingController _modelCtrl;

  @override
  void initState() {
    super.initState();
    final c = widget.config;
    _nameCtrl = TextEditingController(text: c?.name ?? '');
    _urlCtrl = TextEditingController(text: c?.baseUrl ?? 'https://api.openai.com/v1');
    _keyCtrl = TextEditingController(text: c?.apiKey ?? '');
    _modelCtrl = TextEditingController(text: c?.defaultModel ?? 'gpt-3.5-turbo');
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _urlCtrl.dispose();
    _keyCtrl.dispose();
    _modelCtrl.dispose();
    super.dispose();
  }

  void _submit() {
    if (_nameCtrl.text.trim().isEmpty || _urlCtrl.text.trim().isEmpty || _keyCtrl.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('请填写完整信息')));
      return;
    }
    final config = ApiConfig(
      id: widget.config?.id ?? DateTime.now().millisecondsSinceEpoch.toString(),
      name: _nameCtrl.text.trim(),
      baseUrl: _urlCtrl.text.trim(),
      apiKey: _keyCtrl.text.trim(),
      defaultModel: _modelCtrl.text.trim(),
    );
    Navigator.pop(context, config);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.config == null ? '添加API' : '编辑API'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: _nameCtrl,
              decoration: const InputDecoration(labelText: '名称（备注）', hintText: '例如：我的OpenAI'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _urlCtrl,
              decoration: const InputDecoration(labelText: 'Base URL', hintText: 'https://api.openai.com/v1'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _keyCtrl,
              obscureText: true,
              decoration: const InputDecoration(labelText: 'API Key', hintText: 'sk-...'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _modelCtrl,
              decoration: const InputDecoration(labelText: '默认模型', hintText: 'gpt-3.5-turbo'),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('取消')),
        ElevatedButton(onPressed: _submit, child: const Text('保存')),
      ],
    );
  }
}
