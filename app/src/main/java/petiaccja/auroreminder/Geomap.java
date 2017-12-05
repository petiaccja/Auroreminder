package petiaccja.auroreminder;


class Geomap {
    private float[] m_data;
    private int m_horizontal, m_vertical;

    public Geomap() {
        m_horizontal = m_vertical = 1;
        m_data = new float[1];
        m_data[0] = 0.0f;
    }


    public Geomap(float[] intensity, int horizontal, int vertical) {
        m_horizontal = horizontal;
        m_vertical = vertical;
        m_data = intensity;
    }


    public float At(float latitude, float longitude) {
        longitude /= 360.f;
        longitude = longitude - (float)Math.floor(longitude);
        latitude = (latitude + 90.f) / 180.f;

        longitude = Math.max(0, Math.min(longitude, 0.999999f));
        latitude = Math.max(0, Math.min(latitude, 0.999999f));

        int x = (int)(m_horizontal * longitude);
        int y = (int)(m_vertical * latitude);

        return m_data[y*m_horizontal + x];
    }

    public float DbgInterpolLong(float longitude) {
        longitude /= 360.f;
        longitude = longitude - (float)Math.floor(longitude);
        longitude = Math.max(0, Math.min(longitude, 0.999999f));

        return longitude;
    }
}
