class ApiConfig {
  final String id;
  String name;
  String baseUrl;
  String apiKey;
  String defaultModel;

  ApiConfig({
    required this.id,
    required this.name,
    required this.baseUrl,
    required this.apiKey,
    this.defaultModel = 'gpt-3.5-turbo',
  });

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'baseUrl': baseUrl,
        'apiKey': apiKey,
        'defaultModel': defaultModel,
      };

  factory ApiConfig.fromJson(Map<String, dynamic> json) => ApiConfig(
        id: json['id'] ?? '',
        name: json['name'] ?? '',
        baseUrl: json['baseUrl'] ?? '',
        apiKey: json['apiKey'] ?? '',
        defaultModel: json['defaultModel'] ?? 'gpt-3.5-turbo',
      );
}
