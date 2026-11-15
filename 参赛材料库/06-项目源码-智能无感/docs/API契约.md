# AI 节律守护 · API 契约

> 从 NestJS 源码提取，共 **130 个接口**，分 **18 个模块**


## ai （7 个）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| POST | `/api/ai/analyze` | analyze | raw @Body( |
| POST | `/api/ai/chat` | chat | raw @Body( |
| POST | `/api/ai/chat/enhanced` | enhancedChat | raw @Body( |
| GET | `/api/ai/report/daily` | getDailyReport | raw @Query('userId' |
| GET | `/api/ai/report/weekly` | getWeeklyReport | raw @Query('userId' |
| POST | `/api/ai/sleep-analysis` | sleepAnalysis | raw @Body( |
| POST | `/api/ai/tts` | textToSpeech | raw @Body( |

## alert （5 个）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| POST | `/api/alert/create` | createAlert | raw @Body( |
| GET | `/api/alert/detail` | getAlertDetail | raw @Query('userId' |
| POST | `/api/alert/handle` | handleAlert | raw @Body( |
| GET | `/api/alert/history` | getAlertHistory | raw @Query('userId' |
| GET | `/api/alert/stats` | getAlertStats | raw @Query('userId' |

## auth （13 个）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| POST | `/api/auth/bind-family` | bindFamily | raw @Headers('authorization' |
| GET | `/api/auth/family-members` | getFamilyMembers | raw @Headers('authorization' |
| POST | `/api/auth/generate-invite` | generateInvite | raw @Headers('authorization' |
| POST | `/api/auth/login` | login | raw @Body( |
| POST | `/api/auth/logout` | logout | raw @Headers('authorization' |
| POST | `/api/auth/register` | register | raw @Body( |
| POST | `/api/auth/send-code` | sendCode | raw @Body( |
| POST | `/api/auth/unbind-family` | unbindFamily | raw @Headers('authorization' |
| GET | `/api/auth/user` | getUser | raw @Headers('authorization' |
| POST | `/api/auth/user/update` | updateUser | raw @Headers('authorization' |
| POST | `/api/auth/verify-code` | verifyCode | raw @Body( |
| GET | `/api/auth/verify-invite` | verifyInvite | raw @Query('code' |
| POST | `/api/auth/wechat-login` | wechatLogin | raw @Body( |

## checkin （5 个）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| POST | `/api/checkin/check` | check | raw @Body( |
| GET | `/api/checkin/health-score` | getHealthScore | raw @Query('userId' |
| GET | `/api/checkin/records` | getRecords | raw @Query('userId' |
| GET | `/api/checkin/stats` | getStats | raw @Query('userId' |
| GET | `/api/checkin/today` | getToday | raw @Query('userId' |

## device （9 个）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| POST | `/api/device/alert/handle` | handleAlert | raw @Body( |
| GET | `/api/device/alerts` | getDeviceAlerts | raw @Query('userId' |
| POST | `/api/device/bind` | bindDevice | raw @Body( |
| POST | `/api/device/connect` | connectDevice | raw @Body( |
| GET | `/api/device/detail` | getDevice | raw @Query('userId' |
| POST | `/api/device/disconnect` | disconnectDevice | raw @Body( |
| GET | `/api/device/list` | getDevices | raw @Query('userId' |
| POST | `/api/device/status` | updateDeviceStatus | raw @Body( |
| DELETE | `/api/device/unbind` | unbindDevice | raw @Body( |

## diet （9 个）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| GET | `/api/diet/advice` | getAdvice | raw @Query('userId' |
| GET | `/api/diet/history` | getHistory | raw @Query('userId' |
| GET | `/api/diet/nutrition` | getNutrition | raw @Query('userId' |
| GET | `/api/diet/preferences` | getPreferences | raw @Query('userId' |
| POST | `/api/diet/preferences` | setPreferences | raw @Body( |
| POST | `/api/diet/record` | addRecord | raw @Body( |
| GET | `/api/diet/stats` | getStats | raw @Query('userId' |
| GET | `/api/diet/today` | getToday | raw @Query('userId' |
| GET | `/api/diet/weekly` | getWeekly | raw @Query('userId' |

## exercise （9 个）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| GET | `/api/exercise/goals` | getGoals | raw @Query('userId' |
| POST | `/api/exercise/goals` | setGoals | raw @Body( |
| GET | `/api/exercise/history` | getHistory | raw @Query('userId' |
| GET | `/api/exercise/monthly` | getMonthly | raw @Query('userId' |
| POST | `/api/exercise/record` | addRecord | raw @Body( |
| GET | `/api/exercise/stats` | getStats | raw @Query('userId' |
| GET | `/api/exercise/today` | getToday | raw @Query('userId' |
| GET | `/api/exercise/types` | getTypeStats | raw @Query('userId' |
| GET | `/api/exercise/weekly` | getWeekly | raw @Query('userId' |

## feedback （3 个）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| GET | `/api/feedback/faq` | getFAQList | — |
| GET | `/api/feedback/history` | getFeedbackHistory | raw @Query('userId' |
| POST | `/api/feedback/submit` | submitFeedback | raw @Body( |

## guardian （21 个）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| POST | `/api/guardian/activity` | recordActivity | raw @Body( |
| GET | `/api/guardian/alerts` | getAlerts | — |
| POST | `/api/guardian/alerts/add` | addAlert | raw @Body( |
| POST | `/api/guardian/alerts/resolve` | resolveAlert | raw @Body( |
| GET | `/api/guardian/contacts` | getContacts | — |
| POST | `/api/guardian/contacts/add` | addContact | raw @Body( |
| POST | `/api/guardian/contacts/remove` | removeContact | raw @Body( |
| POST | `/api/guardian/contacts/update` | updateContact | raw @Body( |
| GET | `/api/guardian/dashboard` | getDashboard | — |
| GET | `/api/guardian/device` | getDeviceStatus | — |
| GET | `/api/guardian/emergency-contacts/:userId` | getEmergencyContacts | raw @Param('userId' |
| POST | `/api/guardian/emergency-contacts/add` | addEmergencyContact | raw @Body( |
| DELETE | `/api/guardian/emergency-contacts/remove` | removeEmergencyContact | raw @Body( |
| GET | `/api/guardian/family/:userId` | getFamilyMembers | raw @Param('userId' |
| POST | `/api/guardian/family/bind` | bindFamily | raw @Body( |
| DELETE | `/api/guardian/family/unbind` | unbindFamily | raw @Body( |
| GET | `/api/guardian/risk/:userId` | assessRisk | raw @Param('userId' |
| POST | `/api/guardian/sos` | triggerSOS | raw @Body( |
| GET | `/api/guardian/sos/history/:userId` | getSOSHistory | raw @Param('userId' |
| GET | `/api/guardian/status` | getElderStatus | — |
| GET | `/api/guardian/status/:userId` | getStatus | raw @Param('userId' |

## location （5 个）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| POST | `/api/location/address` | getAddress | raw @Body( |
| GET | `/api/location/history/:userId` | getLocationHistory | raw @Param('userId' |
| POST | `/api/location/safe-zone` | setSafeZone | raw @Body( |
| POST | `/api/location/safe-zone-check` | checkSafeZone | raw @Body( |
| GET | `/api/location/safe-zones/:userId` | getSafeZones | raw @Param('userId' |

## medicine （8 个）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| GET | `/api/medicine/history` | getHistory | raw @Query('userId' |
| POST | `/api/medicine/record` | recordMedicine | raw @Body( |
| POST | `/api/medicine/reminder` | addReminder | raw @Body( |
| DELETE | `/api/medicine/reminder/:id` | deleteReminder | raw @Param('id' |
| POST | `/api/medicine/reminder/update` | updateReminder | raw @Body( |
| GET | `/api/medicine/reminders` | getReminders | raw @Query('userId' |
| GET | `/api/medicine/stats` | getStats | raw @Query('userId' |
| GET | `/api/medicine/today` | getTodayRecords | raw @Query('userId' |

## monitor （12 个）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| POST | `/api/monitor/alarm-confirm` | confirmAlarm | raw @Body( |
| GET | `/api/monitor/alarm-history/:userId` | getAlarmHistory | raw @Param('userId' |
| POST | `/api/monitor/alarm-record` | recordAlarm | raw @Body( |
| POST | `/api/monitor/analyze-frame` | analyzeFrame | raw @Body( |
| GET | `/api/monitor/history/:userId` | getHistory | raw @Param('userId' |
| GET | `/api/monitor/location-trace/:userId` | getLocationTrace | raw @Param('userId' |
| POST | `/api/monitor/location-update` | updateLocation | raw @Body( |
| GET | `/api/monitor/report-data` | getReportData | — |
| POST | `/api/monitor/text-analyze` | analyzeText | raw @Body( |
| GET | `/api/monitor/video-data` | getVideoData | — |
| POST | `/api/monitor/video-session-end` | endVideoSession | raw @Body( |
| GET | `/api/monitor/voice-data` | getVoiceData | — |

## reminder （10 个）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| GET | `/api/reminder/configs/:userId` | getReminderConfigs | raw @Param('userId' |
| POST | `/api/reminder/configs/create` | createReminder | raw @Body( |
| DELETE | `/api/reminder/configs/delete` | deleteReminder | raw @Body( |
| POST | `/api/reminder/configs/update` | updateReminderConfig | raw @Body( |
| GET | `/api/reminder/history/:userId` | getReminderHistory | raw @Param('userId' |
| GET | `/api/reminder/log` | getLog | raw @Query('userId' |
| POST | `/api/reminder/log` | logReminder | raw @Body( |
| GET | `/api/reminder/settings` | getSettings | raw @Query('userId' |
| POST | `/api/reminder/settings` | syncSettings | raw @Body( |
| POST | `/api/reminder/trigger` | triggerReminder | raw @Body( |

## src （2 个）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| GET | `/api/health` | getHealth | — |
| GET | `/api/hello` | getHello | — |

## statistics （5 个）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| GET | `/api/statistics/advice` | getHealthAdvice | raw @Query('userId' |
| GET | `/api/statistics/daily` | getDailyStats | raw @Query('userId' |
| GET | `/api/statistics/monthly` | getMonthlyStats | raw @Query('userId' |
| GET | `/api/statistics/trend` | getTimeTrend | raw @Query('userId' |
| GET | `/api/statistics/weekly` | getWeeklyStats | raw @Query('userId' |

## user （2 个）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| GET | `/api/user/profile` | getProfile | raw @Query('userId' |
| POST | `/api/user/settings` | updateSettings | raw @Body( |

## weather （3 个）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| GET | `/api/weather/forecast` | getForecast | raw @Query('city' |
| GET | `/api/weather/live` | getLiveWeather | raw @Query('city' |
| GET | `/api/weather/unified` | getUnifiedWeather | raw @Query('city' |

## werun （2 个）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| POST | `/api/werun/decrypt` | decryptWeRunData | raw @Body( |
| POST | `/api/werun/today` | getTodaySteps | — |