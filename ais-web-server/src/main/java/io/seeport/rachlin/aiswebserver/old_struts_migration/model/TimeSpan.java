package io.seeport.rachlin.aiswebserver.AisDecodeMessageStore.model;

/**
 * Model class representing a time interval, to be specified by users.
 */
public class TimeSpan {
  private String startTime;
  private String endTime;

  public String getStartTime() {
    return startTime;
  }

  public String getEndTime() {
    return endTime;
  }

  public void setStartTime(String startTime) {
    // TODO Check formatting
    this.startTime = startTime;
  }

  public void setEndTime(String endTime) {
    // TODO Check formatting
    this.endTime = endTime;
  }

}
