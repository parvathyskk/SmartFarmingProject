from pyspark.sql import SparkSession
from pyspark.sql.functions import when, col
from pyspark.ml.feature import StringIndexer, VectorAssembler
from pyspark.ml.classification import LogisticRegression
from pyspark.ml.evaluation import BinaryClassificationEvaluator
import matplotlib.pyplot as plt

# Start Spark session
spark = SparkSession.builder.appName("SmartFarmingProject").getOrCreate()

# Load CSV
df = spark.read.csv("D:\smart_water\SmartFarmingProject\src\main\scala\TARP.csv", header=True, inferSchema=True)

# Encode 'Status' column: ON → 1, OFF → 0
df = df.withColumn("label", when(col("Status") == "ON", 1).otherwise(0))

# Drop non-numeric columns
df = df.drop("Datetime", "Status")
df = df.na.drop()

from pyspark.ml.feature import Imputer
columns_to_impute = ["Soil Moisture", "Temperature", "ph", " Soil Humidity","Air temperature (C)","Wind speed (Km/h)", 
                     "Air humidity (%)","Wind gust (Km/h)","Pressure (KPa)","rainfall","N","P","K"]  # Add your columns

imputer = Imputer(
    strategy="mean",
    inputCols=columns_to_impute,
    outputCols=columns_to_impute  # Overwrite original columns
)

df = imputer.fit(df).transform(df)
# Define feature columns
feature_cols = [col for col in df.columns if col != "label"]

# Assemble features into a single vector
assembler = VectorAssembler(inputCols=feature_cols, outputCol="features")
df_vector = assembler.transform(df).select("features", "label")

# Split data
train, test = df_vector.randomSplit([0.8, 0.2], seed=42)

# Logistic Regression model
lr = LogisticRegression(featuresCol="features", labelCol="label")
model = lr.fit(train)

# Predict and evaluate
predictions = model.transform(test)
evaluator = BinaryClassificationEvaluator(labelCol="label")
print("Accuracy (AUC):", evaluator.evaluate(predictions))

# Get feature importances (coefficients)
coeffs = model.coefficients.toArray()
importance = list(zip(feature_cols, abs(coeffs)))
importance_sorted = sorted(importance, key=lambda x: x[1], reverse=True)

# Print and find least among N, P, K
print("\nFeature Importances (Logistic Regression Coefficients):")
for feature, weight in importance_sorted:
    print(f"{feature}: {weight:.4f}")

npk_importance = {f: w for f, w in importance if f in ['N', 'P', 'K']}
least_important = min(npk_importance, key=npk_importance.get)

print(f"\nLeast important among N, P, K: {least_important}")
# After training your model...

def predict_watering(new_data):
    """
    Predicts watering requirement (ON/OFF) for new input data
    and explains which feature contributed most to the decision.
    """
    # Convert input data to Spark DataFrame
    new_df = spark.createDataFrame([new_data])
    
    # Apply same preprocessing as training data
    new_df = assembler.transform(new_df)
    
    # Make prediction
    prediction = model.transform(new_df).collect()[0]
    
    # Get prediction (0=OFF, 1=ON)
    watering_needed = "ON" if prediction.prediction == 1 else "OFF"
    
    # Get feature contributions
    features = prediction.features.toArray()
    coefficients = model.coefficients.toArray()
    
    # Calculate each feature's contribution to the decision
    contributions = {
        name: (value * coeff) 
        for name, value, coeff in zip(feature_cols, features, coefficients)
    }
    
    # Find most influential feature
    most_influential = max(contributions.items(), key=lambda x: abs(x[1]))
    
    # Generate explanation
    if watering_needed == "ON":
        reason = f"High {most_influential[0]} value ({most_influential[1]:.2f}) indicates watering is needed"
    else:
        reason = f"Low {most_influential[0]} value ({most_influential[1]:.2f}) indicates no watering needed"
    
    result = {
        "prediction": watering_needed,
        "reason": f"{most_influential[0]} (impact: {most_influential[1]:.4f})",
        "contributions": {k: round(v, 4) for k, v in contributions.items()}
    }
    
    return result

