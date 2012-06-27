javac -classpath jars/*:"/Applications/NetLogo 5.0.1/NetLogo.jar":. -d classes src/Yoshi.java src/Movie.java src/Capture.java
jar cvfm yoshi.jar manifest.txt -C classes .
cp yoshi.jar /Applications/"NetLogo 5.0.1"/extensions/yoshi/yoshi.jar
