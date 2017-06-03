package mskClinic.clinic;

import java.util.Comparator;

/**
 * Created by Jakub on 03.06.2017.
 */
public class PatientInRegistering {
    private int id;
    private double finishTime;

    public PatientInRegistering(int id, double finishTime) {
        this.id = id;
        this.finishTime = finishTime;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public double getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(double finishTime) {
        this.finishTime = finishTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PatientInRegistering that = (PatientInRegistering) o;

        if (getId() != that.getId()) return false;
        return Double.compare(that.getFinishTime(), getFinishTime()) == 0;

    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = getId();
        temp = Double.doubleToLongBits(getFinishTime());
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}

class PatientInRegisteringComparator implements Comparator<PatientInRegistering>{

    @Override
    public int compare(PatientInRegistering o1, PatientInRegistering o2) {
        return Double.compare(o1.getFinishTime(),o2.getFinishTime());
    }
}