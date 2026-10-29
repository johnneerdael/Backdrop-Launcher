import 'dart:typed_data';
import 'package:flauncher/widgets/home_backdrop.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:transparent_image/transparent_image.dart';

class TrackedImage extends MemoryImage {
  final _evictions = <bool>[];
  int get evictions => _evictions.length;
  TrackedImage() : super(Uint8List.fromList(kTransparentImage));
  @override
  Future<bool> evict({
    ImageCache? cache,
    ImageConfiguration configuration = ImageConfiguration.empty,
  }) {
    _evictions.add(true);
    return super.evict(cache: cache, configuration: configuration);
  }
}

void main() {
  Widget scene(ImageProvider image) => MaterialApp(
    home: SizedBox.expand(
      child: HomeBackdrop(
        image: image,
        revision: image,
        scrim: true,
        duration: const Duration(milliseconds: 100),
      ),
    ),
  );

  testWidgets(
    'keeps the old image only for the fade and paints the scrim directly',
    (tester) async {
      final first = TrackedImage();
      final second = TrackedImage();
      await tester.pumpWidget(scene(first));
      await tester.pumpWidget(scene(second));
      expect(first.evictions, 0);
      await tester.pumpAndSettle();
      expect(first.evictions, 1);
      expect(second.evictions, 0);
      expect(find.byKey(const Key('cinematic_scrim')), findsOneWidget);
      expect(
        find.descendant(
          of: find.byType(HomeBackdrop),
          matching: find.byType(Image),
        ),
        findsOneWidget,
      );
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets('rapidly returning to an image does not evict the active image', (
    tester,
  ) async {
    final first = TrackedImage();
    final second = TrackedImage();
    await tester.pumpWidget(scene(first));
    await tester.pumpWidget(scene(second));
    await tester.pumpWidget(scene(first));
    await tester.pumpAndSettle();
    expect(first.evictions, 0);
    expect(second.evictions, 1);
    await tester.pumpWidget(const SizedBox());
    expect(first.evictions, 1);
  });
}
