import tensorflow as tf
from tensorflow.keras.preprocessing.sequence import pad_sequences
import numpy as np
import os
import pickle
import argparse

# --- Configuration ---
# Adjust these paths if you saved the model/tokenizer elsewhere locally
MODEL_SAVE_PATH = "D:/big_data_model_checking/crnn_phishing_model.keras"
TOKENIZER_SAVE_PATH = "D:/big_data_model_checking/tokenizer.pkl"

# --- Load Tokenizer and Model ---
print("--- Loading Tokenizer and Model ---")
try:
    with open(TOKENIZER_SAVE_PATH, 'rb') as handle:
        tokenizer_data = pickle.load(handle)
        char_index = tokenizer_data['char_index']
        MAX_LEN = tokenizer_data['max_len']
        # VOCAB_SIZE is not strictly needed for prediction but good to have
        VOCAB_SIZE = tokenizer_data.get('vocab_size', len(char_index) + 1)
    print(f"Tokenizer loaded. Max length: {MAX_LEN}")
except FileNotFoundError:
    print(f"Error: Tokenizer file not found at {TOKENIZER_SAVE_PATH}. Make sure training was run first.")
    exit()
except Exception as e:
    print(f"Error loading tokenizer: {e}")
    exit()

try:
    model = tf.keras.models.load_model(MODEL_SAVE_PATH)
    print(f"Model loaded successfully from {MODEL_SAVE_PATH}")
except FileNotFoundError:
    print(f"Error: Model file not found at {MODEL_SAVE_PATH}. Make sure training was successful.")
    exit()
except Exception as e:
    print(f"Error loading model: {e}")
    exit()

# --- Prediction Function ---
def predict_url(url_to_check):
    """Preprocesses a single URL and predicts if it's phishing."""
    print(f"\nPredicting for URL: {url_to_check}")
    
    # 1. Preprocess the URL (must match training preprocessing)
    processed_url = url_to_check.lower() # Lowercase
    
    # 2. Convert to sequence using the loaded tokenizer
    sequence = [[char_index.get(char, 0) for char in processed_url]] # Use 0 for unknown chars
    
    # 3. Pad the sequence
    padded_sequence = pad_sequences(sequence, maxlen=MAX_LEN, padding='post', truncating='post')
    print(f"Padded sequence shape: {padded_sequence.shape}")

    # 4. Make prediction
    prediction_prob = model.predict(padded_sequence, verbose=0)[0][0] # Get the probability from the output
    
    # 5. Interpret prediction
    threshold = 0.5
    if prediction_prob >= threshold:
        label = "phishing"
    else:
        label = "legitimate"
        
    return label, prediction_prob

# --- Main Execution --- 
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Predict if a URL is phishing using a trained CRNN model.')
    parser.add_argument('url', type=str, help='The URL to check.')
    
    args = parser.parse_args()
    
    predicted_label, probability = predict_url(args.url)
    
    print(f"\n--- Prediction Result ---")
    print(f"URL: {args.url}")
    print(f"Predicted Label: {predicted_label}")
    print(f"Phishing Probability: {probability:.4f}")
    print("-------------------------")

# Example usage from within Python:
# label, prob = predict_url("http://example-bad-site.com/login.php")
# print(label, prob)
# label, prob = predict_url("https://www.google.com")
# print(label, prob)

