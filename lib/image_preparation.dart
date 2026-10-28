import 'dart:async';
import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter/painting.dart';

Future<bool> validImageBytes(Uint8List bytes) async {
  ui.Codec? codec;
  try {
    codec = await ui.instantiateImageCodec(
      bytes,
      targetWidth: 16,
      targetHeight: 16,
    );
    final frame = await codec.getNextFrame();
    frame.image.dispose();
    return true;
  } catch (_) {
    return false;
  } finally {
    codec?.dispose();
  }
}

ImageProvider wallpaperImage(ImageProvider image) => ResizeImage(
  image,
  height: ui.PlatformDispatcher.instance.views.first.physicalSize.height
      .round()
      .clamp(1, 4320),
);

/// Decode local images into Flutter's normal image cache before presenting them.
Future<bool> prepareImage(ImageProvider image) async {
  final complete = Completer<bool>();
  final stream = image.resolve(ImageConfiguration.empty);
  late final ImageStreamListener listener;
  void finish(bool valid) {
    if (!complete.isCompleted) complete.complete(valid);
    stream.removeListener(listener);
  }

  listener = ImageStreamListener(
    (_, _) => finish(true),
    onError: (_, _) => finish(false),
  );
  stream.addListener(listener);
  return complete.future;
}
