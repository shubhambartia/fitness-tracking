package com.aiservice.service;

import com.aiservice.model.Activity;
import com.aiservice.model.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityMessageListener {

    private final ActivityAIService activityAIService;

    @RabbitListener(queues = "activity.queue")
    public void processActivityMessage(Activity activity) {
        log.info("Received activity message: {}", activity.getId());
        Recommendation recommendation = activityAIService.generateRecommendation(activity);
        log.info("Generated recommendation: {}", recommendation);
    }
}
