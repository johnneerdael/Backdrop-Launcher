import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

abstract final class BundledArtwork {
  static const defaultBackdrop = 'backdrops/fight-club-55e2393686745.jpg';

  static Future<List<String>> _images(String folder) async {
    final manifest = await AssetManifest.loadFromAssetBundle(rootBundle);
    return manifest
        .listAssets()
        .where(
          (key) =>
              key.startsWith('$folder/') &&
              RegExp(
                r'\.(png|jpe?g|webp)$',
                caseSensitive: false,
              ).hasMatch(key),
        )
        .toList()
      ..sort();
  }

  static Future<List<String>> backdrops() => _images('backdrops');
  static Future<List<String>> appImages() => _images('appicons');
}

/// Persist asset identifiers only; arbitrary filesystem paths are never opened.
class AppArtwork {
  final SharedPreferences _prefs;
  final Future<List<String>> _available = BundledArtwork.appImages();
  AppArtwork(this._prefs);

  Future<bool> has(String package) async =>
      (await _available).contains(_prefs.getString('app_artwork_$package'));

  Future<Uint8List?> bytes(String package) async {
    if (!await has(package)) return null;
    final data = await rootBundle.load(
      _prefs.getString('app_artwork_$package')!,
    );
    return data.buffer.asUint8List(data.offsetInBytes, data.lengthInBytes);
  }

  Future<void> set(String package, String asset) async {
    if (!(await _available).contains(asset))
      throw ArgumentError.value(asset, 'asset', 'Choose bundled app artwork');
    await _prefs.setString('app_artwork_$package', asset);
  }

  Future<void> clear(String package) async =>
      _prefs.remove('app_artwork_$package');
}
