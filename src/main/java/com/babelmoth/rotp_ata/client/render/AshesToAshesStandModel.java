package com.babelmoth.rotp_ata.client.render;

import com.github.standobyte.jojo.client.render.entity.model.stand.HumanoidStandModel;
import com.github.standobyte.jojo.client.render.entity.pose.IModelPose;
import com.github.standobyte.jojo.client.render.entity.pose.ModelPose;
import com.github.standobyte.jojo.client.render.entity.pose.RotationAngle;
import com.babelmoth.rotp_ata.entity.AshesToAshesStandEntity;

/**
 * Model for Ashes to Ashes Stand (灰烬归灰烬)
 * 
 * 此替身为群体型，本体不需要可见模型
 */
public class AshesToAshesStandModel extends HumanoidStandModel<AshesToAshesStandEntity> {

    public AshesToAshesStandModel() {
        super();
    }
    
    @Override
    public void afterInit() {
        super.afterInit();
        // 隐藏所有部件
        setAllPartsVisible(false);
    }

    private void setAllPartsVisible(boolean visible) {
        if (head != null) head.visible = visible;
        if (body != null) body.visible = visible;
        if (leftArm != null) leftArm.visible = visible;
        if (rightArm != null) rightArm.visible = visible;
        if (leftLeg != null) leftLeg.visible = visible;
        if (rightLeg != null) rightLeg.visible = visible;
        if (leftForeArm != null) leftForeArm.visible = visible;
        if (rightForeArm != null) rightForeArm.visible = visible;
        if (leftLowerLeg != null) leftLowerLeg.visible = visible;
        if (rightLowerLeg != null) rightLowerLeg.visible = visible;
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
        // 无动作姿势
    }

    // 使用默认的 idle pose 避免 NPE
}
