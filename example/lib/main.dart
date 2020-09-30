import 'dart:async';
import 'dart:io';

import 'package:edge_detection/edge_detection.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() => runApp(new MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _error;
  String _imagePath;

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> getImageFromPlugin() async {
    String imagePath, error;

    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      imagePath = await EdgeDetection.edgeDetection(Options(
        strings: {
          "done": "Klaar",
          "cancel": "Annuleer",
          "scanning": "Maak een foto",
          "cropping": "Bijsnijden",
          "crop": "Opslaan"
        },
        primaryColor: Colors.amber,
      ));
    } on PlatformException {
      error = 'Failed to get cropped image path.';
    }

    if (!mounted) return;

    setState(() {
      _imagePath = imagePath;
      _error = error;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app - Updated'),
        ),
        body: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Center(
              child: RaisedButton(
                onPressed: getImageFromPlugin,
                child: Text('Get image'),
              ),
            ),

            SizedBox(height: 20),

            /// Image
            if (_imagePath != null)
              Center(
                child: SizedBox.fromSize(
                  size: Size(400, 400),
                  child: Image.file(
                    File(_imagePath),
                  ),
                ),
              ),

            /// Error
            if (_error != null) Center(child: Text(_error)),
          ],
        ),
      ),
    );
  }
}
