class Character {
  final String id;
  String name;
  String avatar;
  String personality;
  String greeting;
  String scenario;
  String model;
  double temperature;
  int maxTokens;
  bool ttsEnabled;
  String ttsVoice;

  Character({
    required this.id,
    required this.name,
    this.avatar = '',
    this.personality = '',
    this.greeting = '你好呀～',
    this.scenario = '',
    this.model = 'gpt-3.5-turbo',
    this.temperature = 0.8,
    this.maxTokens = 1024,
    this.ttsEnabled = false,
    this.ttsVoice = '',
  });

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'avatar': avatar,
        'personality': personality,
        'greeting': greeting,
        'scenario': scenario,
        'model': model,
        'temperature': temperature,
        'maxTokens': maxTokens,
        'ttsEnabled': ttsEnabled,
        'ttsVoice': ttsVoice,
      };

  factory Character.fromJson(Map<String, dynamic> json) => Character(
        id: json['id'] ?? '',
        name: json['name'] ?? '',
        avatar: json['avatar'] ?? '',
        personality: json['personality'] ?? '',
        greeting: json['greeting'] ?? '你好呀～',
        scenario: json['scenario'] ?? '',
        model: json['model'] ?? 'gpt-3.5-turbo',
        temperature: (json['temperature'] ?? 0.8).toDouble(),
        maxTokens: json['maxTokens'] ?? 1024,
        ttsEnabled: json['ttsEnabled'] ?? false,
        ttsVoice: json['ttsVoice'] ?? '',
      );
}
