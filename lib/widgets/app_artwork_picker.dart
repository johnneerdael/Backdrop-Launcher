import 'package:flauncher/bundled_artwork.dart';
import 'package:flutter/material.dart';

class AppArtworkPicker extends StatefulWidget {
  const AppArtworkPicker({super.key});

  static Future<String?> show(BuildContext context) => showDialog<String>(
    context: context,
    builder: (_) => const AppArtworkPicker(),
  );

  @override
  State<AppArtworkPicker> createState() => _AppArtworkPickerState();
}

class _AppArtworkPickerState extends State<AppArtworkPicker> {
  late final _images = BundledArtwork.appImages();

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Choose app icon / banner'),
    content: SizedBox(
      width: 620,
      height: 340,
      child: FutureBuilder<List<String>>(
        future: _images,
        builder: (context, snapshot) {
          if (!snapshot.hasData)
            return const Center(child: CircularProgressIndicator());
          final images = snapshot.data!;
          if (images.isEmpty)
            return const Center(child: Text('No bundled app artwork.'));
          return GridView.builder(
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: 3,
              childAspectRatio: 1.5,
              crossAxisSpacing: 8,
              mainAxisSpacing: 8,
            ),
            itemCount: images.length,
            itemBuilder: (context, index) {
              final asset = images[index];
              final name = asset.split('/').last.split('.').first;
              return TextButton(
                autofocus: index == 0,
                onPressed: () => Navigator.of(context).pop(asset),
                child: Image.asset(
                  asset,
                  semanticLabel: name,
                  cacheWidth: 320,
                  fit: BoxFit.contain,
                ),
              );
            },
          );
        },
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.of(context).pop(),
        child: const Text('Cancel'),
      ),
    ],
  );
}
