package com.example.gradproj.EduNest.controller.weeks;

import com.example.gradproj.EduNest.dto.SimpleResponse;
import com.example.gradproj.EduNest.dto.weeks.CreateWeekrequest;
import com.example.gradproj.EduNest.dto.weeks.UpdateWeekRequest;
import com.example.gradproj.EduNest.dto.weeks.WeekResponse;
import com.example.gradproj.EduNest.service.week.WeekService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/week")
@RequiredArgsConstructor
@Tag(
        name = "Week",
        description = "APIS of week functionality create , delete , update , get weeks of mentorShip , get content of this weeks"
)
public class WeekController {
    private final WeekService weekService;

    @PostMapping("/create")
    @Operation(summary = "create week for mentorship")
    public ResponseEntity<SimpleResponse>create(@RequestBody CreateWeekrequest createWeekrequest){
        WeekResponse create=weekService.createWeek(createWeekrequest);
        SimpleResponse response=new SimpleResponse();
        response.addMessage("message","week created successfully");
        response.addMessage("week",create);
        return  ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @DeleteMapping("/{id}")
    @Operation(summary = "delete week by id")
    public ResponseEntity<SimpleResponse>delete(@PathVariable Long id){
        weekService.deleteWeek(id);
        SimpleResponse simpleResponse=new SimpleResponse();
        simpleResponse.addMessage("message", "week deleted successfully");
        return ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }
    @PatchMapping("/{id}")
    @Operation(summary = "update the week title")
    public ResponseEntity<SimpleResponse>update(@PathVariable Long id, @RequestBody UpdateWeekRequest updateWeekRequest) {
        WeekResponse update = weekService.updateWeekTitle(id, updateWeekRequest);

        SimpleResponse response = new SimpleResponse();
        response.addMessage("message", "week updated successfully");
        response.addMessage("week",update);
        return   ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{mentorshipId}/weeks")
    @Operation(summary = "get weeks for a specific mentorship by its id")
    public ResponseEntity<SimpleResponse> getWeeksByMentorship(Long mentorshipId) {
        SimpleResponse response = new SimpleResponse();
        response.addMessage("Status","all weeks retrieved successfully");
        response.addMessage("weeks",weekService.getWeeksByMentorship(mentorshipId));
        return   ResponseEntity.status(HttpStatus.OK).body(response);
    }
    @GetMapping("/{weekId}/contents")
    @Operation(summary = "get the content of the week")
    public ResponseEntity<SimpleResponse>getContents(@PathVariable Long weekId){
        SimpleResponse simpleResponse=new SimpleResponse();
        simpleResponse.addMessage("Status","all weeks retrieved successfully");
        simpleResponse.addMessage("week",weekService.getWeekContents(weekId));
        return   ResponseEntity.status(HttpStatus.OK).body(simpleResponse);
    }

}
