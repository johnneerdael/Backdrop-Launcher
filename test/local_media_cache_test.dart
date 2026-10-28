import 'dart:io';
import 'package:flauncher/local_media_cache.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'opening a cache removes interrupted writes but retains committed files',
    () async {
      final root = await Directory.systemTemp.createTemp(
        'media-cache-recovery',
      );
      addTearDown(() => root.delete(recursive: true));
      final first = LocalMediaCache('test', directory: () async => root);
      await first.writeJson('feed', {'id': 7});
      final abandoned = File(
        '${root.path}/media_launcher/test/interrupted.part',
      );
      await abandoned.writeAsString('partial write');
      final reopened = LocalMediaCache('test', directory: () async => root);
      expect(await reopened.readJson('feed'), {'id': 7});
      expect(await abandoned.exists(), isFalse);
    },
  );
}
