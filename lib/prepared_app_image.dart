import 'package:flutter/painting.dart';
import 'app_image_type.dart';

class PreparedAppImage {
  final AppImageType type;
  final ImageProvider image;
  const PreparedAppImage(this.type, this.image);
}
