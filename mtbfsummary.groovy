<%
import java.awt.Color
import org.jfree.chart.plot.PlotOrientation as Orientation
import org.jfree.chart.ChartFactory
import org.jfree.chart.ChartUtilities
import org.jfree.data.category.DefaultCategoryDataset
import hudson.model.*
now = Calendar.instance
oneWeekInSec = 60 * 60 * 24 * 10
mtbfopJobName = "flamekk.v2.2.moztwlab01.319.mtbf_op"
summaryJobName = "Send MTBF Summary Report"
nodeName = "moztwlab-01"
nodePwd  = "NIGHT77market"
nodeUser = "mozilla"

jobDetailList = []
summaryResult = [:]

main()

def main(){
	mtbfopJob  = Jenkins.instance.getJob(mtbfopJobName)
	summaryJob = Jenkins.instance.getJob(summaryJobName)

	consoleLogFolderPath = [ mtbfopJob.getBuildDir().getParent(), 'configurations', 'axis-label', nodeName, 'builds'].join(File.separator)
	chartImgFolderPath = summaryJob.lastBuild.workspace 

	(jobDetailList, summaryResult) = getRunDetailAndSummary(mtbfopJob, consoleLogFolderPath)
	createChartImgFile(jobDetailList, chartImgFolderPath)
}


def getRunDetailAndSummary(jobObj, logDirPath){
	runDetailList = []
	sumResult = [totalNo:0, totalHrs:0, minHr:9999999, maxHr:-1, failedAllocateDeviceNo:0, stillRunningNo:0, totalCrashNo:0]
	for (build in jobObj.getBuilds()) {
		if (now.time.time/1000 - build.getTimestamp().time.time/1000 < oneWeekInSec){
			jobDetail = [:]
			sumResult['totalNo'] += 1
			jobDetail['stillRunning']  = build.isInProgress()
			if (build.getEnvVars()['MTBF_CONF'] != null){
				jobDetail['mtbfConf'] = build.getEnvVars()['MTBF_CONF'].split('/')[-1]
			}else{
				jobDetail['mtbfConf'] = null 
			}
			jobDetail['memSize'] = build.getEnvVars()['MEMORYSIZE']
			jobDetail['buildID'] = build.getId()
			jobDetail['buildTimeStamp'] = build.getTime().format('yyyy-M-d_H_m_s')
			jobDetail['buildNO'] = build.number
			consoleLogPath = [logDirPath, jobDetail['buildNO'], 'log'].join(File.separator)
			jobDetail['deviceId'] = getDeviceId(consoleLogPath)
			if (jobDetail['stillRunning'] == false){
				jobDetail['durationInSec'] = Math.round(build.duration/1000*100)/100
			}else{
				jobDetail['durationInSec'] = Math.round(getCurrentRunningTime(consoleLogPath)*100)/100
			}
			jobDetail['durationInHr']  = Math.round(jobDetail['durationInSec']/60/60*100)/100
			if (jobDetail['deviceId'] != "NA" ){
				if (jobDetail['stillRunning'] != true){
					sumResult['totalHrs'] += jobDetail['durationInHr']
					if (jobDetail['durationInHr'] < sumResult['minHr']){
						sumResult['minHr'] = jobDetail['durationInHr']
					}
					if (jobDetail['durationInHr'] > sumResult['maxHr']){
						sumResult['maxHr'] = jobDetail['durationInHr']
					}
				}else{
					sumResult['stillRunningNo'] += 1
				}
				jobDetail['crashNo'] = getCrashNumber(consoleLogPath)
				sumResult['totalCrashNo'] += jobDetail['crashNo']['no']
			}else{
				jobDetail['crashNo'] = [no:0,status:"NA"]
				sumResult['failedAllocateDeviceNo'] += 1
			}
			runDetailList.add(jobDetail)
		}
	}

	sumResult['avgHr'] = Math.round(sumResult['totalHrs'] / (sumResult['totalNo'] - sumResult['failedAllocateDeviceNo'] - sumResult['stillRunningNo']) * 100)/100
	return [runDetailList, sumResult]
}

def getCurrentRunningTime(filePath){
	runningTime = 0
	fileCtnt = new File(filePath).text
	idIndicator = fileCtnt.lastIndexOf('Current MTBF Time:')
	if (idIndicator+40 >= fileCtnt.length()){
		idIndicator = fileCtnt.lastIndexOf('Current MTBF Time:', idIndicator - 1)
	}
        if (idIndicator >= 0){
	        tmpString = fileCtnt.getAt(idIndicator..idIndicator+40)
       	        runningTime = Float.parseFloat(tmpString.getAt(tmpString.indexOf(':')+1..tmpString.indexOf('seconds')-1))
        }
        return runningTime


}

def getDeviceId(filePath){
	deviceId = 'NA'
	fileObj  = new File(filePath)
	if (fileObj.exists()){
		fileCtnt = fileObj.text
		idIndicator = fileCtnt.indexOf('serial')
		if (idIndicator >= 0){
			tmpString = fileCtnt.getAt(idIndicator..idIndicator+25)
        		deviceId = tmpString.getAt(tmpString.indexOf('[')+1..tmpString.indexOf(']')-1)
		}
	}
	return deviceId
}

def createChartImgFile(runList, imgFolderPath){
	chartDataset = new DefaultCategoryDataset()
	chartImagePath = [imgFolderPath, "chart.png"].join(File.separator)
	for (jobCtnt in runList.reverse()) {
		chartDataset.addValue(jobCtnt['durationInHr'], jobCtnt['deviceId'], jobCtnt['buildTimeStamp'].getAt(0..9))
	}
	lineChart = ChartFactory.createLineChart(*["Line Chart","Date","Running Hrs"], chartDataset, Orientation.VERTICAL, *[true,true,true])
	lineChart.setBackgroundPaint(Color.white)
	pngFile = new File(chartImagePath)
	ChartUtilities.saveChartAsPNG(pngFile,lineChart,1000,400)
}

def getCrashNumber(filePath){
	result = [no:0, status:0]
	fileObj = new File(filePath)
	if (fileObj.exists()){
		fileCtnt = fileObj.text
		idIndicator = fileCtnt.lastIndexOf('CrashReportFound') 
		if (idIndicator >= 0){
			tmpString = fileCtnt.getAt(idIndicator..idIndicator+65)
			crashNo = tmpString.getAt(tmpString.indexOf('has')+4..tmpString.indexOf('crashes')-1).toInteger()
			return [no:crashNo, status:crashNo]
		}else{	
			if (fileCtnt.lastIndexOf("CrashReportNotFound") >= 0){
				return [no:0, status:0]
			}else{
				if (fileCtnt.lastIndexOf("CrashReportAdbError") >= 0){
					return [no:0, status:"Can't get data via ADB"]
				}else{
					return [no:0, status:"Can't find crash report keyword in console log"]
				}
			}
		}
	}else{
		return [no:0, status:"Can't find console log file"]
	}
}

def execRemoteCmdViaSsh(userName, rHost, rPwd, execCmd){
	sshLoginCmd  =  "ssh ${userName}@${rHost}"
	expectString = ["spawn", sshLoginCmd, execCmd, ";", "expect", "password",";", "send", '"' + rPwd + "\n" + '"', ";", "expect", rHost, ";"].join(" ")
	shellProcess = ["expect", "-c", expectString].execute()
	shellProcess.waitFor()
	return shellProcess.text
}

%>
<!DOCTYPE html>
<html>

<head>
</head>

<body>
<table style="width:100% " border="1" cellpading="1px" cellspacing="1px">
  <tr>
    <th colspan="8" rowspan="1">Summary</th>
  </tr>
  <tr>
     <th>Build no in total</th>
     <th>Failed allocate device no</th>
     <th>Still Running no</th>
     <th>Total running hours</th>
     <th>Total crash no</th>
     <th>Min running time (hour)</th>
     <th>Max running time (hour)</th>
     <th>Avg running time (hour)</th>
  </tr>
  <tr>
     <th>${summaryResult['totalNo']}</th>
     <th>${summaryResult['failedAllocateDeviceNo']}</th>
     <th>${summaryResult['stillRunningNo']}</th>
     <th>${summaryResult['totalHrs']}</th>
     <th>${summaryResult['totalCrashNo']}</th>
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
    <th colspan="1" rowspan="2">Crash no</th>
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
    <td>${jobDetail['crashNo']['status']}</td>
    <td>${jobDetail['stillRunning']}</td>
  </tr>
<% } %>
</table>

</body>
</html>
