### Skoobot Service - based on Nordic nRF Blinky Android example app

One service

Service UUID: `00001523-1212-EFDE-1523-785FEABCD123`

Two 1 byte characteristics

- First characteristic is a command for the robot
  - UUID: **`00001525-1212-EFDE-1523-785FEABCD123`**

- Second characteristic notifies central (Android Phone)
  - UUID: **`00001524-1212-EFDE-1523-785FEABCD123`**

### Installation and usage

User
-Download github zip
-Download to Android phone over USB

Developer
-download Android Studio
-download Github zip or clone
-compile
-copy to phone over USB
-make crazy features

### Note

* In order to scan for Bluetooth LE device the Location permission must be granted and, on some phones, the Location must be enabled.