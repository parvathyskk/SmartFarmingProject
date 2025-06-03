import json
import sys
from model import predict_watering

def transform_fields(data):
    """Map incoming JSON fields to model expected fields"""
    return {
        "Soil Moisture": float(data.get("soil_moisture", 0)),
        "Temperature": float(data.get("temperature", 0)),
        " Soil Humidity": float(data.get("soil_humidity", 0)),
        "Air temperature (C)": float(data.get("air_temp", 0)),
        "Wind speed (Km/h)": float(data.get("wind_speed", 0)),
        "Air humidity (%)": float(data.get("humidity", 0)),
        "Wind gust (Km/h)": float(data.get("wind_gust", 0)),
        "Pressure (KPa)": float(data.get("pressure", 101.3)),
        "ph": float(data.get("ph", 7.0)),
        "rainfall": float(data.get("rainfall", 0)),
        "N": float(data.get("N", 0)),
        "P": float(data.get("P", 0)),
        "K": float(data.get("K", 0))
    }

if __name__ == "__main__":
    try:
        # Read JSON from stdin
        json_input = sys.stdin.read()
        data = json.loads(json_input)
        
        # Transform fields to model format
        input_data = transform_fields(data)
        
        # Get prediction
        result = predict_watering(input_data)
        
        # Output as JSON
        print(json.dumps(result))
        
    except Exception as e:
        print(json.dumps({"error": str(e)}))
        sys.exit(1)