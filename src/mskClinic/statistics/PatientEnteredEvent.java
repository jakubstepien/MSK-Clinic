package mskClinic.statistics;

/**
 * Created by Jakub on 27.06.2017.
 */
public class PatientEnteredEvent {
    private int Id;
    private double Time;

    public PatientEnteredEvent(int id, double time) {
        Id = id;
        Time = time;
    }

    public int getId() {
        return Id;
    }

    public void setId(int id) {
        Id = id;
    }

    public double getTime() {
        return Time;
    }

    public void setTime(double time) {
        Time = time;
    }
}
