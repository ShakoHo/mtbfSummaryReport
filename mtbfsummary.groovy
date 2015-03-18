<%
import java.awt.Color
import org.jfree.chart.plot.PlotOrientation as Orientation
import org.jfree.chart.ChartFactory
import org.jfree.chart.ChartUtilities
import org.jfree.data.category.DefaultCategoryDataset
import hudson.model.*
now = Calendar.instance
oneWeekInSec = 60 * 60 * 24 * 7
mtbfopJobName = "flamekk.v2.2.moztwlab01.319.mtbf_op"
summaryJobName = "Send MTBF Summary Report"

jobDetailList = []
summaryResult = [:]

main()

def main(){
	mtbfopJob  = Jenkins.instance.getJob(mtbfopJobName)
	summaryJob = Jenkins.instance.getJob(summaryJobName)

	consoleLogFolderPath = [ mtbfopJob.getBuildDir().getParent(), 'configurations', 'axis-label', 'moztwlab-01', 'builds'].join(File.separator)
	chartImgFolderPath = summaryJob.lastBuild.workspace 

	(jobDetailList, summaryResult) = getRunDetailAndSummary(mtbfopJob, consoleLogFolderPath)
	createChartImgFile(jobDetailList, chartImgFolderPath)
}


def getRunDetailAndSummary(jobObj, logDirPath){
	runDetailList = []
	sumResult = [totalNo:0, totalHrs:0, minHr:9999999, maxHr:-1]
	for (build in jobObj.getBuilds()) {
		if (now.time.time/1000 - build.getTimestamp().time.time/1000 < oneWeekInSec){
			jobDetail = [:]
			sumResult['totalNo'] += 1
			if (build.getEnvVars()['MTBF_CONF'] != null){
				jobDetail['mtbfConf'] = build.getEnvVars()['MTBF_CONF'].split('/')[-1]
			}else{
				jobDetail['mtbfConf'] = null 
			}
			jobDetail['memSize'] = build.getEnvVars()['MEMORYSIZE']
			jobDetail['buildID'] = build.getId()
			jobDetail['buildNO'] = build.number
			jobDetail['durationInSec'] = build.duration
			jobDetail['durationInHr']  = build.duration/1000/60/60
			sumResult['totalHrs'] += jobDetail['durationInHr']
			if (jobDetail['durationInHr'] < sumResult['minHr']){
				sumResult['minHr'] = jobDetail['durationInHr']
			}
			if (jobDetail['durationInHr'] > sumResult['maxHr']){
				sumResult['maxHr'] = jobDetail['durationInHr']
			}

			jobDetail['stillRunning']  = build.isInProgress()
			consoleLogPath = [logDirPath, jobDetail['buildNO'], 'log'].join(File.separator)
			consoleLogCtnt = new File(consoleLogPath).text
			idIndicator = consoleLogCtnt.indexOf('serial')
			if (idIndicator >= 0){
				tmpString = consoleLogCtnt.getAt(idIndicator..idIndicator+25)
				jobDetail['deviceId'] = tmpString.getAt(tmpString.indexOf('[')+1..tmpString.indexOf(']')-1)
			}else{
				jobDetail['deviceId'] = 'NA'
			}
			runDetailList.add(jobDetail)
		}
	}

	sumResult['avgHr'] = sumResult['totalHrs'] / sumResult['totalNo']
	return [runDetailList, sumResult]
}

def createChartImgFile(runList, imgFolderPath){
 

	chartDataset = new DefaultCategoryDataset()
	chartImagePath = [imgFolderPath, "chart.png"].join(File.separator)

	for (jobCtnt in runList.reverse()) {
		chartDataset.addValue(jobCtnt['durationInHr'], jobCtnt['deviceId'], jobCtnt['buildID'].getAt(0..9))
	}
	lineChart = ChartFactory.createLineChart(*["Line Chart","Date","Running Hrs"], chartDataset, Orientation.VERTICAL, *[true,true,true])
	lineChart.setBackgroundPaint(Color.white)
	pngFile = new File(chartImagePath)
	ChartUtilities.saveChartAsPNG(pngFile,lineChart,1000,400)
}
%>
<!DOCTYPE html>
<html>

<head>
</head>

<body>
<table style="width:100% " border="1" cellpading="1px" cellspacing="1px">
  <tr>
    <th colspan="5" rowspan="1">Summary</th>
  </tr>
  <tr>
     <th>Build no in total</th>
     <th>Total running hours</th>
     <th>Min running time (hour)</th>
     <th>Max running time (hour)</th>
     <th>Avg running time (hour)</th>
  </tr>
  <tr>
     <th>${summaryResult['totalNo']}</th>
     <th>${summaryResult['totalHrs']}</th>
     <th>${summaryResult['minHr']}</th>
     <th>${summaryResult['maxHr']}</th>
     <th>${summaryResult['avgHr']}</th>
  </tr>
</table>
<hr>
<table style="width:100% " border="1" cellpading="1px" cellspacing="1px">
  <tr>
    <th colspan="2" rowspan="1">Parameters</th>
    <th colspan="1" rowspan="2">Build ID</th>
    <th colspan="1" rowspan="2">Build No</th>
    <th colspan="1" rowspan="2">Device ID</th>
    <th colspan="1" rowspan="2">Running Time (sec)</th>
    <th colspan="1" rowspan="2">Running Time (hour)</th>
    <th colspan="1" rowspan="2">Still Running?</th>
  </tr>
  <tr>
    <th>Memory Report</th>
    <th>Memory Specify</th>

  </tr>
<% for (jobDetail in jobDetailList) { %>

  <tr>
    <td>${jobDetail['mtbfConf']}</td>
    <td>${jobDetail['memSize']}</td>
    <td>${jobDetail['buildID']}</td>		
    <td>${jobDetail['buildNO']}</td>		
    <td>${jobDetail['deviceId']}</td>		
    <td>${jobDetail['durationInSec']}</td>
    <td>${jobDetail['durationInHr']}</td>
    <td>${jobDetail['stillRunning']}</td>
  </tr>
<% } %>
</table>

</body>
</html>
