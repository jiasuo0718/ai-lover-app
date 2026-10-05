import 'package:flutter_tts/flutter_tts.dart';

class TtsService {
  final FlutterTts _flutterTts = FlutterTts();
  bool _initialized = false;

  Future<void> _init() async {
    if (_initialized) return;
    await _flutterTts.setLanguage('zh-CN');
    await _flutterTts.setSpeechRate(0.5);
    await _flutterTts.setVolume(1.0);
    await _flutterTts.setPitch(1.0);
    _initialized = true;
  }

  Future<void> speak(String text) async {
    if (text.isEmpty) return;
    await _init();
    await _flutterTts.stop();
    await _flutterTts.speak(text);
  }

  Future<void> stop() async {
    await _flutterTts.stop();
  }

  Future<void> setVoice(String voice) async {
    await _init();
    // flutter_tts 对中文语音支持有限，这里保留接口
  }

  Future<List<String>> getAvailableVoices() async {
    await _init();
    try {
      final voices = await _flutterTts.getVoices;
      if (voices is List) {
        return voices.map((e) => e.toString()).toList();
      }
      return [];
    } catch (_) {
      return [];
    }
  }
}
