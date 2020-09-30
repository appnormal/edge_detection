import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class EdgeDetection {
  static const MethodChannel _channel = const MethodChannel('edge_detection');

  static Future<String> get detectEdge async {
    final String version = await _channel.invokeMethod('edge_detect', {});
    return version;
  }

  static Future<String> edgeDetection(Options options) async {
    final String version = await _channel.invokeMethod('edge_detect', options.toMap());
    return version;
  }
}

class Options {
  final Map<String, String> strings;
  final Color primaryColor;

  Options({
    this.strings,
    this.primaryColor,
  });

  Map<String, dynamic> toMap() =>
      {
        'strings': strings,
        'color': primaryColor?.value,
      };
}
