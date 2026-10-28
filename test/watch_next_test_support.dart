import 'package:flutter/foundation.dart';
import 'package:flauncher/models/watch_next_item.dart';
import 'package:flauncher/providers/watch_next_service.dart';
import 'package:transparent_image/transparent_image.dart';

class WatchNextTestFeed extends ChangeNotifier implements WatchNextService {
  List<WatchNextItem> values = List.generate(
    5,
    (i) => WatchNextItem(
      id: i + 1,
      title: 'Program ${i + 1}',
      posterUri: 'poster:${i + 1}',
      progressPercent: 35,
    ),
  );
  WatchNextItem? launched;
  bool allowed = true;
  int selected = 0;
  int permissionRequests = 0;
  int permissionRechecks = 0;
  @override
  bool get hasVisibleSection => !allowed || values.isNotEmpty;
  @override
  bool get hasItems => values.isNotEmpty;
  @override
  WatchNextItem? get activeHeroItem =>
      values.isEmpty ? null : values[selected.clamp(0, values.length - 1)];
  void replace(List<WatchNextItem> items) {
    values = items;
    notifyListeners();
  }

  @override
  List<WatchNextItem> get items => values;
  @override
  bool get hasPermission => allowed;
  @override
  bool get isLoading => false;
  @override
  int get activeHeroIndex => selected;
  @override
  void setActiveHeroIndex(int index) {
    selected = index;
    notifyListeners();
  }

  @override
  String? getPosterUriForItem(WatchNextItem? item) => item?.posterUri;
  @override
  Uint8List? getCachedPoster(String? uri) => kTransparentImage;
  @override
  bool hasPosterLoadFailed(String? uri) => false;
  @override
  void ensurePosterLoaded(String? uri) {}
  @override
  Future<void> launchItem(WatchNextItem item) async {
    launched = item;
  }

  @override
  Future<void> requestPermission() async {
    permissionRequests++;
  }

  @override
  Future<void> refreshPermissionAndItems() async {
    permissionRechecks++;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}
