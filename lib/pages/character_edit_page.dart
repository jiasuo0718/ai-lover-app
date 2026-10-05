import 'package:flutter/material.dart';
import '../models/character.dart';

class CharacterEditPage extends StatefulWidget {
  final Character character;
  const CharacterEditPage({super.key, required this.character});

  @override
  State<CharacterEditPage> createState() => _CharacterEditPageState();
}

class _CharacterEditPageState extends State<CharacterEditPage> {
  late TextEditingController _nameCtrl;
  late TextEditingController _greetingCtrl;
  late TextEditingController _personalityCtrl;
  late TextEditingController _scenarioCtrl;
  late TextEditingController _modelCtrl;
  double _temperature = 0.8;
  int _maxTokens = 1024;
  bool _ttsEnabled = false;

  @override
  void initState() {
    super.initState();
    final c = widget.character;
    _nameCtrl = TextEditingController(text: c.name);
    _greetingCtrl = TextEditingController(text: c.greeting);
    _personalityCtrl = TextEditingController(text: c.personality);
    _scenarioCtrl = TextEditingController(text: c.scenario);
    _modelCtrl = TextEditingController(text: c.model);
    _temperature = c.temperature;
    _maxTokens = c.maxTokens;
    _ttsEnabled = c.ttsEnabled;
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _greetingCtrl.dispose();
    _personalityCtrl.dispose();
    _scenarioCtrl.dispose();
    _modelCtrl.dispose();
    super.dispose();
  }

  void _save() {
    if (_nameCtrl.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('角色名称不能为空')),
      );
      return;
    }
    final updated = Character(
      id: widget.character.id,
      name: _nameCtrl.text.trim(),
      greeting: _greetingCtrl.text.trim(),
      personality: _personalityCtrl.text.trim(),
      scenario: _scenarioCtrl.text.trim(),
      model: _modelCtrl.text.trim().isEmpty ? 'gpt-3.5-turbo' : _modelCtrl.text.trim(),
      temperature: _temperature,
      maxTokens: _maxTokens,
      ttsEnabled: _ttsEnabled,
      ttsVoice: widget.character.ttsVoice,
      avatar: widget.character.avatar,
    );
    Navigator.pop(context, updated);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('编辑角色'),
        actions: [
          TextButton(
            onPressed: _save,
            child: const Text('保存', style: TextStyle(color: Colors.green, fontSize: 16, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _buildSection('基本信息', [
            _buildTextField('角色名称', _nameCtrl, '例如：小樱'),
            const SizedBox(height: 12),
            _buildTextField('开场白', _greetingCtrl, '角色见到你说的第一句话', maxLines: 2),
          ]),
          const SizedBox(height: 20),
          _buildSection('角色设定', [
            _buildTextField('性格设定', _personalityCtrl, '描述角色的性格、说话方式、习惯等', maxLines: 4),
            const SizedBox(height: 12),
            _buildTextField('场景设定', _scenarioCtrl, '你们之间的关系、背景故事等（可选）', maxLines: 3),
          ]),
          const SizedBox(height: 20),
          _buildSection('模型参数', [
            _buildTextField('模型名称', _modelCtrl, '例如：gpt-3.5-turbo / deepseek-chat'),
            const SizedBox(height: 16),
            Row(
              children: [
                const Text('Temperature:', style: TextStyle(fontSize: 14)),
                Expanded(
                  child: Slider(
                    value: _temperature,
                    min: 0,
                    max: 2,
                    divisions: 20,
                    label: _temperature.toStringAsFixed(1),
                    onChanged: (v) => setState(() => _temperature = v),
                  ),
                ),
                Text(_temperature.toStringAsFixed(1), style: const TextStyle(fontSize: 13)),
              ],
            ),
            Row(
              children: [
                const Text('Max Tokens:', style: TextStyle(fontSize: 14)),
                Expanded(
                  child: Slider(
                    value: _maxTokens.toDouble(),
                    min: 256,
                    max: 4096,
                    divisions: 15,
                    label: _maxTokens.toString(),
                    onChanged: (v) => setState(() => _maxTokens = v.toInt()),
                  ),
                ),
                Text('$_maxTokens', style: const TextStyle(fontSize: 13)),
              ],
            ),
          ]),
          const SizedBox(height: 20),
          _buildSection('语音', [
            SwitchListTile(
              title: const Text('启用语音朗读'),
              subtitle: const Text('AI回复后自动朗读'),
              value: _ttsEnabled,
              onChanged: (v) => setState(() => _ttsEnabled = v),
            ),
          ]),
          const SizedBox(height: 30),
        ],
      ),
    );
  }

  Widget _buildSection(String title, List<Widget> children) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.green)),
        const SizedBox(height: 12),
        ...children,
      ],
    );
  }

  Widget _buildTextField(String label, TextEditingController ctrl, String hint, {int maxLines = 1}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500)),
        const SizedBox(height: 6),
        TextField(
          controller: ctrl,
          maxLines: maxLines,
          decoration: InputDecoration(
            hintText: hint,
            hintStyle: TextStyle(color: Colors.grey[400]),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
            contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          ),
        ),
      ],
    );
  }
}
