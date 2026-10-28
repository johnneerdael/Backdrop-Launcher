import 'package:flauncher/models/watch_next_item.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('WuPlay millisecond positions become bounded resume progress', () {
    WatchNextItem item(int position, int duration) => WatchNextItem.fromMap({
      'id': 1,
      'title': 'Movie',
      'packageName': 'app.wuplay.androidtv',
      'duration': duration,
      'playbackPosition': position,
      'intentUri': 'wuplay://play/movie/tt0137523',
    });
    expect(item(2700000, 6000000).progressPercent, 45);
    expect(item(7000000, 6000000).progressPercent, 100);
    expect(item(-100, 6000000).progressPercent, 0);
    expect(item(100, 0).progressPercent, 0);
    expect(item(2700000, 6000000).intentUri, 'wuplay://play/movie/tt0137523');
  });
}
