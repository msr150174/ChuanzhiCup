  private generateOfflineResponse(message: string, userContext?: {
    healthScore?: number;
    todaySteps?: number;
    sleepHours?: number;
  }): string {
    const lowerMessage = message.toLowerCase();
    
    // 根据用户消息生成个性化回复
    if (lowerMessage.includes('你好') || lowerMessage.includes('嗨') || lowerMessage.includes('hi')) {
      return '你好呀~ 😊 我是你的健康守护助手小护！今天感觉怎么样？有什么想和我聊聊的吗？ 💙';
    }
    
    if (lowerMessage.includes('健康') || lowerMessage.includes('身体')) {
      if (userContext?.healthScore) {
        return `根据你今天的健康数据，你的健康评分是${userContext.healthScore}分，状态不错哦！💪 今日步数${userContext.todaySteps || 0}步，继续保持！ 🌟`;
      }
      return '健康是最重要的财富！ 💪 我可以帮你记录健康数据、提醒服药、分析作息规律。想了解哪方面呢？ 📊';
    }
    
    if (lowerMessage.includes('步数') || lowerMessage.includes('运动')) {
      if (userContext?.todaySteps) {
        return `你今天已经走了${userContext.todaySteps}步，真棒！🏃‍♀️ 适当运动对身体很好，继续保持这个好习惯！ 💪`;
      }
      return '运动是保持健康的好方法！ 🏃‍♀️ 我可以帮你记录每日步数和运动情况，建议每天走6000-10000步哦！ 💪';
    }
    
    if (lowerMessage.includes('睡眠') || lowerMessage.includes('睡觉')) {
      if (userContext?.sleepHours) {
        return `你昨晚睡了${userContext.sleepHours}小时，睡眠质量对健康很重要！ 😴 建议保持规律作息，每天睡7-8小时最佳哦！ 🌙`;
      }
      return '良好的睡眠是健康的基石！ 😴 我可以帮你分析睡眠规律，建议每天保持7-8小时的睡眠时间。 🌙';
    }
    
    if (lowerMessage.includes('提醒') || lowerMessage.includes('服药')) {
      return '服药提醒是我的重要功能！ 💊 我可以帮你设置服药提醒，确保按时吃药。需要我帮你设置吗？ ⏰';
    }
    
    if (lowerMessage.includes('心情') || lowerMessage.includes('情绪') || lowerMessage.includes('压力')) {
      return '心情和情绪健康同样重要！ 💙 有什么心事可以和我说说，我会认真倾听的。有时候倾诉一下会感觉轻松很多呢~ 🌸';
    }
    
    if (lowerMessage.includes('谢谢') || lowerMessage.includes('感谢')) {
      return '不客气呀~ 😊 能够帮到你我很开心！有什么其他问题随时找我聊聊哦！ 💙';
    }
    
    // 默认回复
    return `我收到你的消息了~ 😊 作为你的健康守护助手，我可以帮你：\n\n💪 记录健康数据\n💊 设置服药提醒\n🏃‍♀️ 分析运动情况\n😴 关注睡眠质量\n💙 倾听你的心事\n\n有什么需要帮助的吗？`;
  }
