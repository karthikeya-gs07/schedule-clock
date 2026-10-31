# Release shrinking (R8) rules for the phone app, on top of Android's standard optimize rules.

# WorkManager (calendar sync) builds its Room database by reflection; without this the app crashed at launch.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
