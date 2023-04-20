# XFree-MPAndroidChart

[ ***中文文档*** ](https://github.com/oneAcorn/XFree-MPAndroidChart/blob/master/CHINESES_README.md)

XFree-MPAndroidChart is based on [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) 3.1.0, The purpose of this library is to solve the problem that the LineChart of [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) can't add unsorted entries.
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

## Preview

![github](https://github.com/oneAcorn/XFree-MPAndroidChart/blob/master/docs/pic0.png)

use XFreeLineDataSet.mPointVisibleThreshold to determined whether the circles are displayed

```kotlin
//when mPointVisibleThreshold<=0 the circles on the lines will always displayed.
//when mPointVisibleThreshold>0,
//Whether the circles are displayed or not is determined by the number of entries which simultaneously shown on the screen
//When the number of entries shown on the screen is less than the mPointVisibleThreshold, those circles will be displayed.
mXFreeLineDataSet.mPointVisibleThreshold = 100
```

![github](https://github.com/oneAcorn/XFree-MPAndroidChart/blob/master/docs/diplay_circles_by_screen.gif)

Select Area

![github](https://github.com/oneAcorn/XFree-MPAndroidChart/blob/master/docs/select%20area.png)

![github](https://github.com/oneAcorn/XFree-MPAndroidChart/blob/master/docs/select_area.gif)


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

Most of the usages for XFree-MPAndroidChart are the same as for [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart).

The differences are shown below.

1. Create the instance object of chart view:`AAChartView`
```xml
    <com.acorn.xfreechart.library.XFreeLineChart
    android:id="@+id/lineChart"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
  ```

2. Create a LineDataSet
```kotlin
//In XFree-MPAndroidChart we use the XFreeLineDataSet instead of the LineDataSet
val set = XFreeLineDataSet(binding.lineChart, null, "Test Data")
```

3 Create the Highlighter
```kotlin
//In XFree-MPAndroidChart we use the XFreeHighlighter instead of the ChartHighlighter
xFreeChart.highlighter = XFreeHighlighter(this)
```
