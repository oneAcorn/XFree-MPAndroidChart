# XFree-MPAndroidChart

XFree-MPAndroidChart is extended from [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart), The purpose of this library is to solve the problem that the LineChart of [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) can't add unsorted entries.
When adding unsorted entries to [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart), it will crash and the following error is displayed

```java
E/AndroidRuntime: FATAL EXCEPTION: main
    Process: com.acorn.myframeapp, PID: 13758
    java.lang.NegativeArraySizeException: -2
        at com.github.mikephil.charting.utils.Transformer.generateTransformedValuesLine(Transformer.java:178)
        at com.github.mikephil.charting.renderer.LineChartRenderer.drawValues(LineChartRenderer.java:567)
        at com.github.mikephil.charting.charts.BarLineChartBase.onDraw(BarLineChartBase.java:297)
```

Note that the performance of XFree-MPAndroidChart is slightly inferior to that of [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart)

## Installation


### Gradle


#### Step 1. Add the JitPack repository to your build file

Add it in your root build.gradle at the end of the repositories:

```groovy
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```
	
#### Step 2. Add the dependency

```groovy
	dependencies {
	        implementation 'com.github.oneAcorn:XFree-MPAndroidChart:v1.0.10'
	}
```

## Usage

Most of the usage for XFree-MPAndroidChart are the same as for [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart).

The differences are shown below.

1. Create the instance object of chart view:`AAChartView`
```xml
    <com.acorn.xfreechart.library.XFreeLineChart
    android:id="@+id/lineChart"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
  ```

2. Create LineDataSet
```kotlin
//In XFree-MPAndroidChart we use XFreeLineDataSet instead of LineDataSet
val set = XFreeLineDataSet(binding.lineChart, null, "Test Data")
//when mPointVisibleThreshold<=0 the circles on the lines will always display.
//when mPointVisibleThreshold>0,
//Whether the circles are displayed is determined by the number of entries displayed simultaneously on the screen.
//when the number of entries displayed on the screen less than mPointVisibleThreshold, the circles will display
set.mPointVisibleThreshold = 100
```

3 Create Highlighter
```kotlin
//In XFree-MPAndroidChart we use XFreeHighlighter instead of ChartHighlighter
xFreeChart.highlighter = XFreeHighlighter(this)
```
