package com.spartronics4915.frc2019.subsystems;

import com.spartronics4915.frc2019.Constants;
import com.spartronics4915.frc2019.ControlBoard;
import com.spartronics4915.lib.drivers.A21IRSensor;
import com.spartronics4915.lib.drivers.A41IRSensor;
import com.spartronics4915.lib.drivers.IRSensor;
import com.spartronics4915.lib.util.CANProbe;
import com.spartronics4915.lib.util.ILoop;
import com.spartronics4915.lib.util.ILooper;

import edu.wpi.first.hal.sim.mockdata.PCMDataJNI;
import edu.wpi.first.wpilibj.DoubleSolenoid;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.DoubleSolenoid.Value;

public class Climber extends Subsystem
{

    private static Climber mInstance = null;

    public static Climber getInstance()
    {
        if (mInstance == null)
        {
            mInstance = new Climber();
        }
        return mInstance;
    }

    public enum WantedState
    {
        DISABLE, CLIMB, RETRACT_FRONT_STRUTS, RETRACT_REAR_STRUTS,
    }

    private enum SystemState
    {
        DISABLING, CLIMBING, RETRACTING_FRONT_STRUTS, RETRACTING_REAR_STRUTS,
    }

    private WantedState mWantedState = WantedState.DISABLE;
    private SystemState mSystemState = SystemState.DISABLING;
    private DoubleSolenoid mFrontLeftClimberSolenoid = null;
    private DoubleSolenoid mFrontRightClimberSolenoid = null;
    private DoubleSolenoid mRearLeftClimberSolenoid = null;
    private DoubleSolenoid mRearRightClimberSolenoid = null;
    public IRSensor mFrontRightIRSensor = null;
    public IRSensor mFrontLeftIRSensor = null;
    public IRSensor mDownwardFrontLeftIRSensor = null;
    public IRSensor mDownwardFrontRightIRSensor = null;
    public IRSensor mDownwardRearLeftIRSensor = null;
    public IRSensor mDownwardRearRightIRSensor = null;

    private Climber()

    {
        boolean success = false;
        try
        {
            if (!CANProbe.getInstance().validatePCMId(Constants.kClimberPCMId)) throw new RuntimeException("Climber PCM isn't on the CAN bus!");

            mFrontLeftClimberSolenoid = new DoubleSolenoid(Constants.kClimberPCMId, Constants.kFrontLeftSolenoidId1,
                    Constants.kFrontLeftSolenoidId2);
            mFrontRightClimberSolenoid = new DoubleSolenoid(Constants.kClimberPCMId, Constants.kFrontRightSolenoidId1,
                    Constants.kFrontRightSolenoidId2);
            mRearLeftClimberSolenoid = new DoubleSolenoid(Constants.kClimberPCMId, Constants.kRearLeftSolenoidId1,
                    Constants.kRearLeftSolenoid2);
            mRearRightClimberSolenoid = new DoubleSolenoid(Constants.kClimberPCMId, Constants.kRearRightSolenoidId1,
                    Constants.kRearRightSolenoidId2);
            mFrontRightIRSensor = new A41IRSensor(Constants.kFrontLeftIRSensorId);
            mFrontLeftIRSensor = new A21IRSensor(Constants.kFrontRightIRSensorId);
            mDownwardFrontLeftIRSensor = new A21IRSensor(Constants.kDownwardFrontLeftIRSensorId);
            mDownwardFrontRightIRSensor = new A21IRSensor(Constants.kDownwardFrontRightIRSensorId);
            mDownwardRearLeftIRSensor = new A21IRSensor(Constants.kDownwardRearLeftIRSensorId);
            mDownwardRearRightIRSensor = new A21IRSensor(Constants.kDownwardRearRightIRSensorId);
        }
        catch (Exception e)
        {
            success = false;
            logException("Couldn't instantiate hardware", e);
        }

        logInitialized(success);
    }

    private final ILoop mLoop = new ILoop()
    {

        public boolean mStateChanged = true;

        @Override
        public void onStart(double timestamp)
        {
            synchronized (Climber.this)
            {
                mWantedState = WantedState.DISABLE;
                mSystemState = SystemState.DISABLING;
            }
        }

        @Override
        public void onLoop(double timestamp)
        {
            synchronized (Climber.this)
            {
                SystemState newState = defaultStateTransfer();
                switch (mSystemState)
                {
                    case DISABLING:
                        // Climber is disabled (Will be like this until the last 30 seconds of the
                        // match)
                        // Make sure tanks are at acceptable levels for climbing (Check before intiating
                        // CLIMBING)
                        if (mStateChanged)
                        {
                            mFrontLeftClimberSolenoid.set(Value.kReverse);
                            mFrontRightClimberSolenoid.set(Value.kReverse);
                            mRearLeftClimberSolenoid.set(Value.kReverse);
                            mRearRightClimberSolenoid.set(Value.kReverse);
                        }
                        break;

                    case CLIMBING:
                        // Struts will extend from their dormant position to allow the robot to reach
                        // the height required to get to L3
                        // Must be done when robot is flushed with L3 (Done with distance sensors and a
                        // backup encoder reading)
                        if (mStateChanged)
                        {
                            mFrontLeftClimberSolenoid.set(Value.kForward);
                            mFrontRightClimberSolenoid.set(Value.kForward);
                            mRearLeftClimberSolenoid.set(Value.kForward);
                            mRearRightClimberSolenoid.set(Value.kForward);
                        }
                        mDownwardFrontLeftIRSensor.getVoltage(); // XXX: What are you doing with this? It just returns a double.
                        break;

                    case RETRACTING_FRONT_STRUTS:
                        // Solenoids from the front struts will retract when they become flushed with L3
                        // Done with distance sensors and backup driver vision
                        if (mStateChanged)
                        {
                            mFrontLeftClimberSolenoid.set(Value.kReverse);
                            mFrontLeftClimberSolenoid.set(Value.kReverse);
                        }
                        break;

                    case RETRACTING_REAR_STRUTS:
                        // Solenoids from the rear struts will retract when the robot can support its
                        // own weight on L3
                        // Done primarily with driver vision, but distance sensor might be used
                        if (mStateChanged)
                        {
                            mRearLeftClimberSolenoid.set(Value.kReverse);
                            mRearRightClimberSolenoid.set(Value.kReverse);
                        }
                        break;

                    default:
                        logError("Unhandled system state!");
                }
                mSystemState = newState; // XXX: Are you sure it's like this?
                if (newState != mSystemState)
                    mStateChanged = false;
                else
                    mStateChanged = true;
            }
        }

        @Override
        public void onStop(double timestamp)
        {
            synchronized (Climber.this)
            {
                stop();
            }
        }
    };

    private SystemState defaultStateTransfer()
    {
        SystemState newState = mSystemState;
        switch (mWantedState)
        {
            case DISABLE:
                newState = SystemState.DISABLING;
                break;
            case CLIMB:
                newState = SystemState.CLIMBING;
                break;
            case RETRACT_FRONT_STRUTS:
                newState = SystemState.RETRACTING_FRONT_STRUTS;
                break;
            case RETRACT_REAR_STRUTS:
                newState = SystemState.RETRACTING_REAR_STRUTS;
                break;
            default:
                newState = SystemState.DISABLING;
                logNotice("Robot is in an Unhandled Wanted State!");
                break;
        }
        return newState;
    }

    public synchronized void setWantedState(WantedState wantedState)
    {
        mWantedState = wantedState;
    }

    public synchronized boolean atTarget()
    {
        switch (mWantedState)
        {
            case DISABLE:
                return mSystemState == SystemState.DISABLING;
            case CLIMB:
                return mSystemState == SystemState.CLIMBING;
            case RETRACT_FRONT_STRUTS:
                if (mDownwardFrontLeftIRSensor.getDistance() <= Constants.kIRSensorTriggerDistance)
                    return mSystemState == SystemState.RETRACTING_FRONT_STRUTS;
                else
                    return false;
            case RETRACT_REAR_STRUTS:
            { // XXX: unless there's a serious reason for having brackets that I am unaware of, probably remove these
                if (mDownwardRearLeftIRSensor.getDistance() <= Constants.kIRSensorTriggerDistance)
                    return mSystemState == SystemState.RETRACTING_REAR_STRUTS;
                else
                    return false;
            }
            default:
                logError("Climber in unhandled Wanted State!");
                return false;
        }
    }

    @Override
    public void registerEnabledLoops(ILooper enabledLooper)
    {
        enabledLooper.register(mLoop);
    }

    @Override
    public boolean checkSystem(String variant)
    {
        logNotice("Lifting for 5 Seconds");
        mFrontLeftClimberSolenoid.set(Value.kForward);
        mFrontRightClimberSolenoid.set(Value.kForward);
        mRearLeftClimberSolenoid.set(Value.kForward);
        mRearRightClimberSolenoid.set(Value.kForward);
        Timer.delay(5);
        mFrontLeftClimberSolenoid.set(Value.kReverse);
        mFrontRightClimberSolenoid.set(Value.kReverse);
        mRearLeftClimberSolenoid.set(Value.kReverse);
        mRearRightClimberSolenoid.set(Value.kReverse);
        Timer.delay(2);

        logNotice("Testing IR Sensors");
        mFrontLeftIRSensor.getDistance();
        mFrontLeftIRSensor.getVoltage();
        logNotice("Front Left IR Sensor Distance is " + mFrontLeftIRSensor.getDistance() + " and Voltage is "
                + mFrontLeftIRSensor.getVoltage());
        Timer.delay(5);
        mFrontRightIRSensor.getDistance();
        mFrontRightIRSensor.getVoltage();
        logNotice("Front Right IR Sensor Distance is " + mFrontRightIRSensor.getDistance() + " and Voltage is "
                + mFrontRightIRSensor.getVoltage());
        Timer.delay(5);
        mDownwardFrontLeftIRSensor.getDistance();
        mDownwardFrontLeftIRSensor.getVoltage();
        logNotice("Downward Front Left IR Sensor Distance is " + mDownwardFrontLeftIRSensor.getDistance()
                + " and Voltage is " + mDownwardFrontLeftIRSensor.getVoltage());
        Timer.delay(5);
        mDownwardFrontRightIRSensor.getDistance();
        mDownwardFrontRightIRSensor.getVoltage();
        logNotice("Downward Front Right IR Sensor Distance is " + mDownwardFrontRightIRSensor.getDistance()
                + " and Voltage is " + mDownwardFrontRightIRSensor.getVoltage());
        Timer.delay(5);
        mDownwardRearLeftIRSensor.getDistance();
        mDownwardRearLeftIRSensor.getVoltage();
        logNotice("Downward Rear Left IR Sensor Distance is " + mDownwardRearLeftIRSensor.getDistance()
                + " and Voltage is " + mDownwardRearLeftIRSensor.getVoltage());
        Timer.delay(5);
        mDownwardRearRightIRSensor.getDistance();
        mDownwardRearRightIRSensor.getVoltage();
        logNotice("Downward Rear Right Sensor Distance is " + mDownwardRearRightIRSensor.getDistance()
                + " and Voltage is " + mDownwardRearRightIRSensor.getVoltage());
        Timer.delay(5);
        return true;
    }

    @Override
    public void outputTelemetry()
    {
        dashboardPutState(mSystemState.toString());
        dashboardPutWantedState(mWantedState.toString());
        dashboardPutNumber("Voltage of IR Sensor is ", mFrontLeftIRSensor.getVoltage());
        dashboardPutNumber("Distance readout of IR Sensor is ", mFrontLeftIRSensor.getDistance());
    }

    @Override
    public void stop()
    {
        // Stop your hardware here
    }
}
