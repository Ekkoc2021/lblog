package com.yang.lblogserver.blog.service;

public class RankConfig {
    private final double weightLike;
    private final double weightComment;
    private final double weightView;
    private final int decayBase;
    private final double decayExponent;

    public RankConfig(double weightLike, double weightComment, double weightView,
                      int decayBase, double decayExponent) {
        this.weightLike = weightLike;
        this.weightComment = weightComment;
        this.weightView = weightView;
        this.decayBase = decayBase;
        this.decayExponent = decayExponent;
    }

    public double getWeightLike() { return weightLike; }
    public double getWeightComment() { return weightComment; }
    public double getWeightView() { return weightView; }
    public int getDecayBase() { return decayBase; }
    public double getDecayExponent() { return decayExponent; }
}
