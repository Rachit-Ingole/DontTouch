#!/usr/bin/env python
import sys
import os
import numpy as np
import tensorflow as tf
from tensorflow.keras.models import load_model
from tensorflow.keras.preprocessing import image
import json

# Define waste categories
CATEGORIES = ["Paper", "Glass", "Metal", "Plastic", "Trash"]

class WasteClassifier:
    def __init__(self, model_path):
        """Initialize the waste classifier with the H5 model path"""
        self.model = load_model(model_path)
        
    def preprocess_image(self, img_path):
        """Preprocess image for classification"""
        img = image.load_img(img_path, target_size=(224, 224))
        img_array = image.img_to_array(img)
        img_array = np.expand_dims(img_array, axis=0)
        img_array = img_array / 255.0  # Normalize
        return img_array
        
    def classify(self, img_path):
        """Classify image and return prediction results"""
        try:
            img_array = self.preprocess_image(img_path)
            predictions = self.model.predict(img_array)
            
            # Get the top prediction
            top_prediction_idx = np.argmax(predictions[0])
            top_prediction_score = float(predictions[0][top_prediction_idx])
            top_category = CATEGORIES[top_prediction_idx]
            
            # Get all predictions with their confidence scores
            all_predictions = []
            for i, score in enumerate(predictions[0]):
                all_predictions.append({
                    "category": CATEGORIES[i],
                    "confidence": float(score)
                })
            
            result = {
                "success": True,
                "category": top_category,
                "confidence": top_prediction_score,
                "all_predictions": all_predictions
            }
        except Exception as e:
            result = {
                "success": False,
                "error": str(e)
            }
            
        return result

def main():
    """Main function to run the classifier from command line"""
    if len(sys.argv) != 3:
        print("Usage: python waste_classifier.py <model_path> <image_path>")
        return
        
    model_path = sys.argv[1]
    image_path = sys.argv[2]
    
    if not os.path.exists(model_path):
        print(json.dumps({"success": False, "error": f"Model not found: {model_path}"}))
        return
        
    if not os.path.exists(image_path):
        print(json.dumps({"success": False, "error": f"Image not found: {image_path}"}))
        return
    
    classifier = WasteClassifier(model_path)
    result = classifier.classify(image_path)
    
    # Output JSON result for Java to parse
    print(json.dumps(result))

if __name__ == "__main__":
    main()