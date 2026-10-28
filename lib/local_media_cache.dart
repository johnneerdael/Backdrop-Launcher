import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:path_provider/path_provider.dart';

/// Best-effort, atomic local storage. A missing cache never prevents launching.
class LocalMediaCache {
  static final _activeTemporaryFiles = <String>{};
  final String namespace;
  final Future<Directory> Function() _directory;
  Future<Directory?>? _root;
  final _writes = <String, Future<void>>{};
  int _temporaryId = 0;

  LocalMediaCache(this.namespace, {Future<Directory> Function()? directory})
    : _directory = directory ?? getApplicationCacheDirectory {
    if (!RegExp(r'^[a-z_]+$').hasMatch(namespace)) {
      throw ArgumentError.value(namespace, 'namespace');
    }
  }

  String _name(String key) => '${sha256.convert(utf8.encode(key))}.bin';

  Future<Directory?> _open() => _root ??= () async {
    try {
      final dir = await Directory(
        '${(await _directory()).path}/media_launcher/$namespace',
      ).create(recursive: true);
      await for (final file in dir.list()) {
        if (file is File &&
            file.path.endsWith('.part') &&
            !_activeTemporaryFiles.contains(file.path)) {
          await file.delete();
        }
      }
      return dir;
    } catch (_) {
      return null;
    }
  }();

  Future<Uint8List?> readBytes(
    String key, {
    int maxBytes = 16 * 1024 * 1024,
  }) async {
    try {
      final dir = await _open();
      if (dir == null) return null;
      final file = File('${dir.path}/${_name(key)}');
      if (!await file.exists() || await file.length() > maxBytes) return null;
      return await file.readAsBytes();
    } catch (_) {
      return null;
    }
  }

  Future<dynamic> readJson(String key) async {
    try {
      final bytes = await readBytes(key);
      return bytes == null ? null : jsonDecode(utf8.decode(bytes));
    } catch (_) {
      return null;
    }
  }

  Future<void> writeJson(String key, Object value) =>
      writeBytes(key, Uint8List.fromList(utf8.encode(jsonEncode(value))));

  Future<void> writeBytes(String key, Uint8List bytes) {
    final previous = _writes[key] ?? Future<void>.value();
    late final Future<void> pending;
    pending = previous
        .then((_) async {
          File? temporary;
          try {
            final dir = await _open();
            if (dir == null) return;
            final path = '${dir.path}/${_name(key)}';
            temporary = File(
              '$path.${DateTime.now().microsecondsSinceEpoch}.${_temporaryId++}.part',
            );
            _activeTemporaryFiles.add(temporary.path);
            await temporary.writeAsBytes(bytes, flush: true);
            await temporary.rename(path);
          } catch (_) {
            try {
              if (temporary != null && await temporary.exists())
                await temporary.delete();
            } catch (_) {}
          } finally {
            if (temporary != null) _activeTemporaryFiles.remove(temporary.path);
          }
        })
        .whenComplete(() {
          if (identical(_writes[key], pending)) _writes.remove(key);
        });
    _writes[key] = pending;
    return pending;
  }

  Future<void> remove(String key) async {
    try {
      await _writes[key];
      final dir = await _open();
      if (dir == null) return;
      final file = File('${dir.path}/${_name(key)}');
      if (await file.exists()) await file.delete();
    } catch (_) {}
  }

  Future<void> prune(Set<String> retainedKeys) async {
    try {
      final dir = await _open();
      if (dir == null) return;
      final retained = retainedKeys.map(_name).toSet();
      await for (final entry in dir.list()) {
        final name = entry.uri.pathSegments.last;
        if (entry is File &&
            name.endsWith('.bin') &&
            !retained.contains(name)) {
          await entry.delete();
        }
      }
    } catch (_) {}
  }
}
