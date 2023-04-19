# XFree-MPAndroidChart

[ ***English Document*** ](https://github.com/oneAcorn/XFree-MPAndroidChart/blob/master/README.md)

XFree-MPAndroidChart 继承自 [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart),实现此库的目的是解决[MPAndroidChart](https://github.com/PhilJay/MPAndroidChart)的LineDataChart的X轴无法添加未排序数据(只能添加递增的x轴数据)的问题
当添加未排序数据到[MPAndroidChart](https://github.com/PhilJay/MPAndroidChart)时,会报如下错误

```java
E/AndroidRuntime: FATAL EXCEPTION: main
    Process: com.acorn.myframeapp, PID: 13758
    java.lang.NegativeArraySizeException: -2
        at com.github.mikephil.charting.utils.Transformer.generateTransformedValuesLine(Transformer.java:178)
        at com.github.mikephil.charting.renderer.LineChartRenderer.drawValues(LineChartRenderer.java:567)
        at com.github.mikephil.charting.charts.BarLineChartBase.onDraw(BarLineChartBase.java:297)
```

注意: XFree-MPAndroidChart的性能略低于[MPAndroidChart](https://github.com/PhilJay/MPAndroidChart)

## Preview

![github](https://github.com/oneAcorn/XFree-MPAndroidChart/blob/master/docs/pic0.png)

使用XFreeLineDataSet.mPointVisibleThreshold决定是否该显示圆点.当屏幕上点数少于此阈值时才显示 

```kotlin
//when mPointVisibleThreshold<=0 the circles on the lines will always displayed.
//when mPointVisibleThreshold>0,
//Whether the circles are displayed or not is determined by the number of entries which simultaneously shown on the screen
//When the number of entries shown on the screen is less than the mPointVisibleThreshold, those circles will be displayed.
mXFreeLineDataSet.mPointVisibleThreshold = 100
```

![github](https://github.com/oneAcorn/XFree-MPAndroidChart/blob/master/docs/diplay_circles_by_screen.gif)

选区模式

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

XFree-MPAndroidChart大多数使用方式和[MPAndroidChart](https://github.com/PhilJay/MPAndroidChart)相同.

不同点如下

1. Create the instance object of chart view:`AAChartView`
```xml
    <com.acorn.xfreechart.library.XFreeLineChart
    android:id="@+id/lineChart"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
  ```

2. 创建LineDataSet
```kotlin
//In XFree-MPAndroidChart we use the XFreeLineDataSet instead of the LineDataSet
val set = XFreeLineDataSet(binding.lineChart, null, "Test Data")
```

3 创建Highlighter(就是点击后那个定位的十字线)
```kotlin
//In XFree-MPAndroidChart we use the XFreeHighlighter instead of the ChartHighlighter
xFreeChart.highlighter = XFreeHighlighter(this)
```
