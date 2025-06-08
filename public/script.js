
  const output = document.getElementById('output');
  const apiBase = 'http://localhost:8080'; // change if your server runs elsewhere

  // Show/hide sections on menu button click
  document.querySelectorAll('#menu button').forEach(btn => {
    btn.addEventListener('click', () => {
      const sectionId = btn.getAttribute('data-section');
      document.querySelectorAll('.section').forEach(sec => {
        sec.style.display = (sec.id === sectionId) ? 'block' : 'none';
      });
      output.textContent = '';
    });
  });

  // Manual Entry submit
  document.getElementById('manualForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const formData = new FormData(e.target);
    const data = {
  soilMoisture: Number(formData.get('soil_moisture')),
  temperature: Number(formData.get('temperature')),
  airHumidity: Number(formData.get('air_humidity')),
  ph: Number(formData.get('ph')),
  rainfall: Number(formData.get('rainfall')),
  n: Number(formData.get('N')),
  p: Number(formData.get('P')),
  k: Number(formData.get('K'))
};


    try {
      const res = await fetch(`${apiBase}/insert`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      });
      output.textContent = res.ok ? 'Sensor data inserted successfully!' : 'Failed to insert data.';
      if(res.ok) e.target.reset();
    } catch (err) {
      output.textContent = 'Error: ' + err.message;
    }
  });

  // CSV upload
  function parseCSV(text) {
  const lines = text.trim().split('\n');
  const headers = lines[0].split(',').map(h => h.trim().toLowerCase());
  return lines.slice(1).map(line => {
    const values = line.split(',').map(v => v.trim());
    let obj = {};
    headers.forEach((h, i) => {
      const val = values[i];
      // Normalize field names to match backend expectations
      switch (h) {
        case 'soil_moisture': obj.soilMoisture = Number(val); break;
        case 'temperature': obj.temperature = Number(val); break;
        case 'air_humidity': obj.airHumidity = Number(val); break;
        case 'ph': obj.ph = Number(val); break;
        case 'rainfall': obj.rainfall = Number(val); break;
        case 'n': obj.n = parseInt(val); break;
        case 'p': obj.p = parseInt(val); break;
        case 'k': obj.k = parseInt(val); break;
      }
    });

    // ✅ Fallback defaults for any missing fields
    obj.soilMoisture ??= 0;
    obj.temperature ??= 0;
    obj.airHumidity ??= 0;
    obj.ph ??= 0;
    obj.rainfall ??= 0;
    obj.n ??= 0;
    obj.p ??= 0;
    obj.k ??= 0;

    return obj;
  });
}


  document.getElementById('uploadCsvBtn').addEventListener('click', async () => {
    const fileInput = document.getElementById('csvFileInput');
    if (!fileInput.files.length) {
      output.textContent = 'Please select a CSV file first.';
      return;
    }
    const file = fileInput.files[0];
    const text = await file.text();
    let dataRows;
    try {
      dataRows = parseCSV(text).slice(0, 10);
    } catch (err) {
      output.textContent = 'Failed to parse CSV: ' + err.message;
      return;
    }

    output.textContent = `Uploading ${dataRows.length} rows...`;
    for (let i = 0; i < dataRows.length; i++) {
      try {
        const res = await fetch(`${apiBase}/insert`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(dataRows[i]),
        });
        if (!res.ok) {
          output.textContent = `Failed to insert row ${i + 1}`;
          return;
        }
      } catch (err) {
        output.textContent = `Error inserting row ${i + 1}: ${err.message}`;
        return;
      }
    }
    output.textContent = `Successfully uploaded ${dataRows.length} rows!`;
    fileInput.value = '';
  });

  

  // View all records
  document.getElementById('btnFetchAll').addEventListener('click', async () => {
    try {
      const res = await fetch(`${apiBase}/all`);
      if (res.ok) {
        const data = await res.json();
        if (data.length === 0) {
          output.textContent = 'No sensor data found.';
          return;
        }
        output.textContent = JSON.stringify(data, null, 2);
      } else {
        output.textContent = 'Failed to fetch sensor data.';
      }
    } catch (err) {
      output.textContent = 'Error: ' + err.message;
    }
  });

  // Update record by timestamp
  document.getElementById('updateForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const formData = new FormData(e.target);
  const timestamp = formData.get('timestamp');
  if (!timestamp) {
    output.textContent = 'Timestamp is required.';
    return;
  }

  let updated = false;
  output.textContent = '';

  for (const [key, value] of formData.entries()) {
    if (key.toLowerCase() !== 'timestamp' && value !== '')
{
      const url = `${apiBase}/update?timestamp=${encodeURIComponent(timestamp)}&field=${encodeURIComponent(key)}&value=${encodeURIComponent(value)}`;
      const res = await fetch(url, { method: 'PUT' });
      if (res.ok) {
        output.textContent += `✔ Updated ${key}\n`;
        updated = true;
      } else {
        output.textContent += `✖ Failed to update ${key}\n`;
      }
    }
  }

  if (!updated) {
    output.textContent = 'No fields were updated.';
  } else {
    e.target.reset();
  }
});


  // Delete record by timestamp
  document.getElementById('deleteForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const formData = new FormData(e.target);
    const timestamp = formData.get('timestamp');
    if (!timestamp) {
      output.textContent = 'Timestamp is required.';
      return;
    }
    try {
      // Assuming backend supports DELETE /sensor/delete?timestamp=...
      const res = await fetch(`${apiBase}/delete?timestamp=${encodeURIComponent(timestamp)}`, {
        method: 'DELETE',
      });
      output.textContent = res.ok ? 'Record deleted successfully.' : 'Failed to delete record.';
      if(res.ok) e.target.reset();
    } catch (err) {
      output.textContent = 'Error: ' + err.message;
    }
  });

  // document.getElementById("btnPredictWater")?.addEventListener("click", () => {
  //   document
  //     .querySelectorAll(".section")
  //     .forEach((sec) => (sec.style.display = "none"));
  //   document.getElementById("predictWaterOutput").style.display = "block";
  //   output.textContent = "";
  // });

  // Water Prediction Button Handler
document.getElementById("checkWaterBtn").addEventListener("click", async () => {
  const output = document.getElementById("predictionOutput");
  output.innerHTML = "<p class='loading'>Loading prediction...</p>";

  try {
    const response = await fetch('http://localhost:8080/api/predict_water');

    if (!response.ok) {
      throw new Error(`Server error: ${response.status}`);
    }

    let rawText = await response.text();
    console.log("Raw response:", rawText);

    const jsonStart = rawText.indexOf('{');
    const jsonEnd = rawText.lastIndexOf('}');
    if (jsonStart === -1 || jsonEnd === -1 || jsonEnd <= jsonStart) {
      throw new Error("Could not find valid JSON in response");
    }

    let jsonStr = rawText.substring(jsonStart, jsonEnd + 1);
    console.log("Extracted JSON:", jsonStr);

    // Fix escaped quotes
    jsonStr = jsonStr.replace(/\\"/g, '"');

    const result = JSON.parse(jsonStr);

    const prediction = result.prediction || "UNKNOWN";
    const reason = result.reason || "No reason provided";
    const contributions = result.contributions || {};

    output.innerHTML = `
      <div class="prediction-result">
        <h3 style="color: ${prediction === "ON" ? 'green' : 'red'}">
          Recommendation: ${prediction}
        </h3>
        <p><strong>Reason:</strong> ${reason}</p>
        <div class="contributions">
         <p><strong>Contributions</strong>
          ${Object.entries(contributions)
            .sort((a, b) => Math.abs(b[1]) - Math.abs(a[1]))
            .slice(0, 3)
            .map(([k, v]) => `
              <div class="factor">
                <span>${k}:</span>
                <span style="color: ${v > 0 ? 'green' : 'red'}">
                  ${v?.toFixed(4) || 'N/A'}
                </span>
              </div>`
            ).join('')}
        </div>
      </div>
    `;
  } catch (error) {
    console.error("Full error:", error);
    output.innerHTML = `<p class="error">Error: ${error.message}</p>`;
  }
});
