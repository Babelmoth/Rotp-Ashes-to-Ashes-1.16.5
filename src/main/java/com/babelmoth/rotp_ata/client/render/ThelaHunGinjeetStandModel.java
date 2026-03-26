package com.babelmoth.rotp_ata.client.render;

import com.babelmoth.rotp_ata.entity.ThelaHunGinjeetStandEntity;
import com.github.standobyte.jojo.client.render.entity.model.stand.HumanoidStandModel;
import com.github.standobyte.jojo.client.render.entity.pose.ModelPose;
import com.github.standobyte.jojo.client.render.entity.pose.RotationAngle;

public class ThelaHunGinjeetStandModel extends HumanoidStandModel<ThelaHunGinjeetStandEntity> {

    public ThelaHunGinjeetStandModel() {
        super();
    }

    @Override
    protected ModelPose<ThelaHunGinjeetStandEntity> initIdlePose() {
        return new ModelPose<>(new RotationAngle[] {
                RotationAngle.fromDegrees(body, 0f, -10f, 0f),
                RotationAngle.fromDegrees(leftArm, -10f, 0f, -5f),
                RotationAngle.fromDegrees(rightArm, -10f, 0f, 5f),
                RotationAngle.fromDegrees(leftLeg, 5f, 0f, -2f),
                RotationAngle.fromDegrees(rightLeg, -5f, 0f, 2f)
        });
    }

    @Override
    protected RotationAngle[][] initSummonPoseRotations() {
        return new RotationAngle[][] {
            new RotationAngle[] {},
            new RotationAngle[] {}
        };
    }

    @Override
    protected void initActionPoses() {
    }
}
