<%
import java.awt.Color
import org.jfree.chart.plot.PlotOrientation as Orientation
import org.jfree.chart.ChartFactory
import org.jfree.chart.ChartUtilities
import org.jfree.data.category.DefaultCategoryDataset
import hudson.model.*
now = Calendar.instance
noDaysBefore = 3
oneWeekInSec = 60 * 60 * 24 * 10
oneDayInSec = 60 * 60 * 24
mtbfopJobName = "flamekk.vmaster.moztwlab01.512"
summaryJobName = "Send MTBF Summary Report"
nodeName = "moztwlab-01"

jobDetailList = []
summaryResult = [:]
buildInfo = [:]

main()

def main(){
	mtbfopJob  = Jenkins.instance.getJob(mtbfopJobName)
	summaryJob = Jenkins.instance.getJob(summaryJobName)

	consoleLogFolderPath = [ mtbfopJob.getBuildDir().getParent(), 'configurations', 'axis-label', nodeName, 'builds'].join(File.separator)
	chartImgFolderPath = summaryJob.lastBuild.workspace 

	(jobDetailList, summaryResult) = getRunDetailAndSummary(mtbfopJob, consoleLogFolderPath)
	buildInfo = getBuildInfo(mtbfopJob,consoleLogFolderPath)
}

def getBuildInfo(jobObj, logDirPath){
        result = [buildId:"", buildDate:""]
	builds = getBuildObjInDuration(jobObj)
        if (builds.size() > 0){
		for (build in builds){
			consoleLogPath =  [logDirPath, build.number, 'log'].join(File.separator)
			result['buildDate'] = build.getTime().format('yyyy-M-d')
        		fileObj = new File(consoleLogPath)
		        if (fileObj.exists()){
        		        fileCtnt = fileObj.text
                		idIndicator = fileCtnt.indexOf('Build ID')
	                	if (idIndicator >= 0){
        	                	if (idIndicator+23 <= fileCtnt.length()){
                	                	tmpString = fileCtnt.getAt(idIndicator+9..idIndicator+23)
						result['buildId'] = tmpString
						break
		                         }else{
        		                        tmpString = fileCtnt.getAt(idIndicator+9..fileCtnt.length())
						result['buildId'] = tmpString
						break
	                        	}
	        	        }else{
					result['buildId'] = "Can't find build id in console log"
	        	        }
	        	}else{
				result['buildId'] = "Can't find build id in console log"
	        	}
		}
	}else{
		result['buildId'] = "No build today!"
		result['buildDate'] = "No build today!"
	}
	return result	

}

def getBuildObjInDuration(jobObj){
	builds = []
	for (build in jobObj.getBuilds()) {
                timeDiff = now.time.time/1000 - build.getTimestamp().time.time/1000
                minTimeDiff = (noDaysBefore - 1) * oneDayInSec
                maxTimeDiff = noDaysBefore * oneDayInSec 
                if (timeDiff < maxTimeDiff && timeDiff > minTimeDiff){
			builds.add(build)
		}
	}
	return builds
}


def getRunDetailAndSummary(jobObj, logDirPath){
	runDetailList = []
	sumResult = [totalNo:0, totalHrs:0, minHr:9999999, maxHr:-1, failedAllocateDeviceNo:0, stillRunningNo:0, totalCrashNo:0]
	builds = getBuildObjInDuration(jobObj)
	for (build in builds) {
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
        if ((sumResult['totalNo'] - sumResult['failedAllocateDeviceNo'] - sumResult['stillRunningNo']) == 0)
        {
                sumResult['avgHr'] = 0
                sumResult['minHr'] = 0
		sumResult['maxHr'] = 0
        }else{
                sumResult['avgHr'] = Math.round(sumResult['totalHrs'] / (sumResult['totalNo'] - sumResult['failedAllocateDeviceNo'] - sumResult['stillRunningNo']) * 100)/100
        }
	return [runDetailList, sumResult]
}

def getCurrentRunningTime(filePath){
	runningTime = 0
	fileCtnt = new File(filePath).text
	keyword = 'mtbf_operation: Total MTBF Time:'
	idIndicator = fileCtnt.lastIndexOf(keyword)
 	if (idIndicator >= 0){
		if (idIndicator+55 >= fileCtnt.length()){
	                idIndicator = fileCtnt.lastIndexOf(keyword, idIndicator - 1)
        	}
	        if (idIndicator >= 0){
        	        tmpString = fileCtnt.getAt(idIndicator..idIndicator+55)
                	runningTime = Float.parseFloat(tmpString.getAt(tmpString.indexOf(':')+1..tmpString.indexOf('seconds')-1))
        	}		
		idIndicator = fileCtnt.lastIndexOf('Current MTBF Time:')
                if (idIndicator+40 >= fileCtnt.length()){
                        idIndicator = fileCtnt.lastIndexOf('Current MTBF Time:', idIndicator - 1)
                }
                if (idIndicator >= 0){
                        tmpString = fileCtnt.getAt(idIndicator..idIndicator+40)
                        incrementRunningTime = Float.parseFloat(tmpString.getAt(tmpString.indexOf(':')+1..tmpString.indexOf('seconds')-1))
                }
		runningTime+=incrementRunningTime
	}
	else{
		idIndicator = fileCtnt.lastIndexOf('Current MTBF Time:')
		if (idIndicator+40 >= fileCtnt.length()){
			idIndicator = fileCtnt.lastIndexOf('Current MTBF Time:', idIndicator - 1)
		}
        	if (idIndicator >= 0){
	        	tmpString = fileCtnt.getAt(idIndicator..idIndicator+40)
	       	        runningTime = Float.parseFloat(tmpString.getAt(tmpString.indexOf(':')+1..tmpString.indexOf('seconds')-1))
        	}
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


def getCrashNumber(filePath){
	result = [no:0, status:0]
	fileObj = new File(filePath)
	if (fileObj.exists()){
		fileCtnt = fileObj.text
		idIndicator = fileCtnt.lastIndexOf('CrashReportFound') 
		if (idIndicator >= 0){
			if (idIndicator+65 <= fileCtnt.length()){
				tmpString = fileCtnt.getAt(idIndicator..idIndicator+65)
				crashNo = tmpString.getAt(tmpString.indexOf('has')+4..tmpString.indexOf('crashes')-1).toInteger()
	                 }else{
				tmpString = fileCtnt.getAt(idIndicator..fileCtnt.length())
				crashNo = tmpString.getAt(tmpString.indexOf('has')+4..tmpString.indexOf('crashes')-1).toInteger()
			}
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


%>
<!DOCTYPE html>
<html>

<head>
</head>

<body>
<table style="width:100% " border="1" cellpading="1px" cellspacing="1px">
  <tr>
  <td rowspan="3"><h2>MTBF</h2></td>
  <td><h3>Avg Hrs</h3></td>
  <td><h2>${summaryResult['avgHr']}</h2></td>
  </tr>
  <tr>
  <td><h3>Build ID</h4></td>
  <td><h2>${buildInfo['buildId']}</h2></td>
  </tr>
  <tr>
  <td><h3>Build Date</h3></td>
  <td><h2>${buildInfo['buildDate']}</h2></td>
  </tr>
  <tr><td colspan="3"> * You could also get MTBF data on Raptor from the link here:
  <a href="https://raptor.mozilla.org/dashboard/script/mtbf?var-device=flame-kk&var-memory=512&var-branch=master">Raptor</a>
  </td></tr>
</table>

</body>
</html>
